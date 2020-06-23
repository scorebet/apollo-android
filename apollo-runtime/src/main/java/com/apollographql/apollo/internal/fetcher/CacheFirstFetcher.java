package com.apollographql.apollo.internal.fetcher;

import com.apollographql.apollo.api.internal.ApolloLogger;
import com.apollographql.apollo.exception.ApolloException;
import com.apollographql.apollo.fetcher.ResponseFetcher;
import com.apollographql.apollo.interceptor.ApolloInterceptor;
import com.apollographql.apollo.interceptor.ApolloInterceptorChain;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

/**
 * Signals the apollo client to first fetch the data from the normalized cache. If it's not present in the normalized
 * cache or if an exception occurs while trying to fetch it from the normalized cache, then the data is instead fetched
 * from the network.
 */
public final class CacheFirstFetcher implements ResponseFetcher {

  @Override public ApolloInterceptor provideInterceptor(final ApolloLogger apolloLogger) {
    return new CacheFirstInterceptor();
  }

  private static final class CacheFirstInterceptor implements ApolloInterceptor {

    volatile boolean disposed;

    @Override
    public void interceptAsync(@NotNull final InterceptorRequest request, @NotNull final ApolloInterceptorChain chain,
        @NotNull final Executor dispatcher, @NotNull final CallBack callBack) {
      InterceptorRequest cacheRequest = request.toBuilder().fetchFromCache(true).build();
      chain.proceedAsync(cacheRequest, dispatcher, new CallBack() {
        @Override public void onResponse(@NotNull InterceptorResponse response) {
          callBack.onResponse(response);
        }

        @Override public void onFailure(@NotNull ApolloException e) {
          if (!disposed) {
            InterceptorRequest networkRequest = request.toBuilder().fetchFromCache(false).build();
            chain.proceedAsync(networkRequest, dispatcher, callBack);
          }
        }

        @Override public void onCompleted() {
          callBack.onCompleted();
        }

        @Override public void onFetch(FetchSourceType sourceType) {
          callBack.onFetch(sourceType);
        }
      });
    }

    @Override public void dispose() {
      disposed = true;
    }
  }
}
