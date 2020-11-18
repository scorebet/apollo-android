package com.apollographql.apollo.internal.interceptor;

import com.apollographql.apollo.api.Operation;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.api.internal.ApolloLogger;
import com.apollographql.apollo.api.internal.Function;
import com.apollographql.apollo.api.internal.Optional;
import com.apollographql.apollo.api.internal.ResponseFieldMapper;
import com.apollographql.apollo.cache.ApolloCacheHeaders;
import com.apollographql.apollo.cache.normalized.ApolloStore;
import com.apollographql.apollo.cache.normalized.ApolloStoreOperation;
import com.apollographql.apollo.cache.normalized.Record;
import com.apollographql.apollo.exception.ApolloException;
import com.apollographql.apollo.interceptor.ApolloInterceptor;
import com.apollographql.apollo.interceptor.ApolloInterceptorChain;
import com.apollographql.apollo.cache.normalized.internal.ResponseNormalizer;
import com.apollographql.apollo.cache.normalized.internal.Transaction;
import com.apollographql.apollo.cache.normalized.internal.WriteableStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

import static com.apollographql.apollo.api.internal.Utils.checkNotNull;

/**
 * ApolloCacheInterceptor is a concrete {@link ApolloInterceptor} responsible for serving requests from the normalized
 * cache if {@link InterceptorRequest#fetchFromCache} is true. Saves all network responses to cache.
 */
public final class ApolloCacheInterceptor implements ApolloInterceptor {
  final ApolloStore apolloStore;
  private final ResponseFieldMapper responseFieldMapper;
  private final Executor dispatcher;
  private final boolean writeToCacheAsynchronously;
  final ApolloLogger logger;
  volatile boolean disposed;

  public ApolloCacheInterceptor(@NotNull ApolloStore apolloStore, @NotNull ResponseFieldMapper responseFieldMapper,
      @NotNull Executor dispatcher, @NotNull ApolloLogger logger, boolean writeToCacheAsynchronously) {
    this.apolloStore = checkNotNull(apolloStore, "cache == null");
    this.responseFieldMapper = checkNotNull(responseFieldMapper, "responseFieldMapper == null");
    this.dispatcher = checkNotNull(dispatcher, "dispatcher == null");
    this.logger = checkNotNull(logger, "logger == null");
    this.writeToCacheAsynchronously = writeToCacheAsynchronously;
  }

  @Override
  public void interceptAsync(@NotNull final InterceptorRequest request, @NotNull final ApolloInterceptorChain chain,
      @NotNull final Executor dispatcher, @NotNull final CallBack callBack) {
    dispatcher.execute(new Runnable() {
      @Override public void run() {
        if (disposed) return;
        if (request.fetchFromCache) {
          callBack.onFetch(FetchSourceType.CACHE);
          final InterceptorResponse cachedResponse;
          try {
            cachedResponse = resolveFromCache(request);
            callBack.onResponse(cachedResponse);
            callBack.onCompleted();
          } catch (ApolloException e) {
            callBack.onFailure(e);
          }
        } else {
          writeOptimisticUpdatesAndPublish(request);
          chain.proceedAsync(request, dispatcher, new CallBack() {
            @Override public void onResponse(@NotNull InterceptorResponse networkResponse) {
              if (disposed) return;
              cacheResponseAndPublish(request, networkResponse, writeToCacheAsynchronously);
              callBack.onResponse(networkResponse);
              callBack.onCompleted();
            }

            @Override public void onFailure(@NotNull ApolloException t) {
              rollbackOptimisticUpdatesAndPublish(request);
              callBack.onFailure(t);
            }

            @Override public void onCompleted() {
              // call onCompleted in onResponse
            }

            @Override public void onFetch(FetchSourceType sourceType) {
              callBack.onFetch(sourceType);
            }
          });
        }
      }
    });
  }

  @Override public void dispose() {
    disposed = true;
  }

  InterceptorResponse resolveFromCache(InterceptorRequest request) throws ApolloException {
    ResponseNormalizer<Record> responseNormalizer = apolloStore.cacheResponseNormalizer();
    //noinspection unchecked
    ApolloStoreOperation<Response> apolloStoreOperation = apolloStore.read(request.operation, responseFieldMapper,
        responseNormalizer, request.cacheHeaders);
    Response cachedResponse = apolloStoreOperation.execute();
    if (cachedResponse.getData() != null) {
      logger.d("Cache HIT for operation %s", request.operation.name().name());
      return new InterceptorResponse(null, cachedResponse, responseNormalizer.records());
    }
    logger.d("Cache MISS for operation %s", request.operation.name().name());
    throw new ApolloException(String.format("Cache miss for operation %s", request.operation.name().name()));
  }

  Set<String> cacheResponse(final InterceptorResponse networkResponse,
      final InterceptorRequest request) {
    if (networkResponse.parsedResponse.isPresent()
        && networkResponse.parsedResponse.get().hasErrors()
        && !request.cacheHeaders.hasHeader(ApolloCacheHeaders.STORE_PARTIAL_RESPONSES)
    ) {
        return Collections.emptySet();
    }
    final Optional<List<Record>> records = networkResponse.cacheRecords.map(
        new Function<Collection<Record>, List<Record>>() {
          @NotNull @Override public List<Record> apply(@NotNull Collection<Record> records) {
            final List<Record> result = new ArrayList<>(records.size());
            for (Record record : records) {
              result.add(record.toBuilder().mutationId(request.uniqueId).build());
            }
            return result;
          }
        }
    );

    if (!records.isPresent()) {
      return Collections.emptySet();
    }

    try {
      return apolloStore.writeTransaction(new Transaction<WriteableStore, Set<String>>() {
        @Nullable @Override public Set<String> execute(WriteableStore cache) {
          return cache.merge(records.get(), request.cacheHeaders);
        }
      });
    } catch (Exception e) {
      logger.e("Failed to cache operation response", e);
      return Collections.emptySet();
    }
  }

  void cacheResponseAndPublish(InterceptorRequest request, InterceptorResponse networkResponse, boolean async) {
    if (async) {
      dispatcher.execute(new Runnable() {
        @Override public void run() {
          cacheResponseAndPublishSynchronously(request, networkResponse);
        }
      });
    } else {
      cacheResponseAndPublishSynchronously(request, networkResponse);
    }
  }

  void cacheResponseAndPublishSynchronously(InterceptorRequest request, InterceptorResponse networkResponse) {
    try {
      Set<String> networkResponseCacheKeys = cacheResponse(networkResponse, request);
      Set<String> rolledBackCacheKeys = rollbackOptimisticUpdates(request);
      Set<String> changedCacheKeys = new HashSet<>();
      changedCacheKeys.addAll(rolledBackCacheKeys);
      changedCacheKeys.addAll(networkResponseCacheKeys);
      publishCacheKeys(changedCacheKeys);
    } catch (Exception rethrow) {
      rollbackOptimisticUpdatesAndPublish(request);
      throw rethrow;
    }
  }

  void writeOptimisticUpdatesAndPublish(final InterceptorRequest request) {
    dispatcher.execute(new Runnable() {
      @Override public void run() {
        try {
          if (request.optimisticUpdates.isPresent()) {
            Operation.Data optimisticUpdates = request.optimisticUpdates.get();
            apolloStore.writeOptimisticUpdatesAndPublish(request.operation, optimisticUpdates, request.uniqueId)
                .execute();
          }
        } catch (Exception e) {
          logger.e(e, "failed to write operation optimistic updates, for: %s", request.operation);
        }
      }
    });
  }

  void rollbackOptimisticUpdatesAndPublish(final InterceptorRequest request) {
    dispatcher.execute(new Runnable() {
      @Override public void run() {
        try {
          apolloStore.rollbackOptimisticUpdatesAndPublish(request.uniqueId).execute();
        } catch (Exception e) {
          logger.e(e, "failed to rollback operation optimistic updates, for: %s", request.operation);
        }
      }
    });
  }

  Set<String> rollbackOptimisticUpdates(final InterceptorRequest request) {
    try {
      return apolloStore.rollbackOptimisticUpdates(request.uniqueId).execute();
    } catch (Exception e) {
      logger.e(e, "failed to rollback operation optimistic updates, for: %s", request.operation);
      return Collections.emptySet();
    }
  }

  void publishCacheKeys(final Set<String> cacheKeys) {
    dispatcher.execute(new Runnable() {
      @Override public void run() {
        try {
          apolloStore.publish(cacheKeys);
        } catch (Exception e) {
          logger.e(e, "Failed to publish cache changes");
        }
      }
    });
  }
}
