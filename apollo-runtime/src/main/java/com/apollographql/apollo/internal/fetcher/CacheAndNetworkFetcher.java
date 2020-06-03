package com.apollographql.apollo.internal.fetcher;

import com.apollographql.apollo.api.internal.ApolloLogger;
import com.apollographql.apollo.api.internal.Optional;
import com.apollographql.apollo.exception.ApolloException;
import com.apollographql.apollo.fetcher.ResponseFetcher;
import com.apollographql.apollo.interceptor.ApolloInterceptor;
import com.apollographql.apollo.interceptor.ApolloInterceptorChain;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

/**
 * Signal the apollo client to fetch the data from both the network and the cache. If cached data is not present, only
 * network data will be returned. If cached data is available, but network experiences an error, cached data is
 * returned. If cache data is not available, and network data is not available, the error of the network request will be
 * propagated. If both network and cache are available, both will be returned. Cache data is guaranteed to be returned
 * first.
 */
public final class CacheAndNetworkFetcher implements ResponseFetcher {

  @Override public ApolloInterceptor provideInterceptor(ApolloLogger apolloLogger) {
    return new CacheAndNetworkInterceptor();
  }

  private static final class CacheAndNetworkInterceptor implements ApolloInterceptor {

    private Optional<ApolloInterceptor.InterceptorResponse> cacheResponse = Optional.absent();
    private Optional<ApolloInterceptor.InterceptorResponse> networkResponse = Optional.absent();
    private Optional<ApolloException> cacheException = Optional.absent();
    private Optional<ApolloException> networkException = Optional.absent();
    private boolean dispatchedCacheResult;
    private ApolloInterceptor.CallBack originalCallback;
    private volatile boolean disposed;

    @Override
    public void interceptAsync(@NotNull InterceptorRequest request, @NotNull ApolloInterceptorChain chain,
        @NotNull Executor dispatcher, @NotNull final CallBack callBack) {
      if (disposed) return;
      originalCallback = callBack;
      InterceptorRequest cacheRequest = request.toBuilder().fetchFromCache(true).build();
      chain.proceedAsync(cacheRequest, dispatcher, new CallBack() {
        @Override public void onResponse(@NotNull InterceptorResponse response) {
          handleCacheResponse(response);
        }

        @Override public void onFailure(@NotNull ApolloException e) {
          handleCacheError(e);
        }

        @Override public void onCompleted() {
        }

        @Override public void onFetch(FetchSourceType sourceType) {
          callBack.onFetch(sourceType);
        }
      });

      InterceptorRequest networkRequest = request.toBuilder().fetchFromCache(false).build();
      chain.proceedAsync(networkRequest, dispatcher, new CallBack() {
        @Override public void onResponse(@NotNull InterceptorResponse response) {
          handleNetworkResponse(response);
        }

        @Override public void onFailure(@NotNull ApolloException e) {
          handleNetworkError(e);
        }

        @Override public void onCompleted() {
        }

        @Override public void onFetch(FetchSourceType sourceType) {
          callBack.onFetch(sourceType);
        }
      });
    }

    @Override public void dispose() {
      disposed = true;
    }

    synchronized void handleNetworkResponse(ApolloInterceptor.InterceptorResponse response) {
      networkResponse = Optional.of(response);
      dispatch();
    }

    synchronized void handleNetworkError(ApolloException exception) {
      networkException = Optional.of(exception);
      dispatch();
    }

    synchronized void handleCacheResponse(ApolloInterceptor.InterceptorResponse response) {
      cacheResponse = Optional.of(response);
      dispatch();
    }

    synchronized void handleCacheError(ApolloException exception) {
      cacheException = Optional.of(exception);
      dispatch();
    }

    private synchronized void dispatch() {
      if (disposed) {
        return;
      }
      if (!dispatchedCacheResult) {
        if (cacheResponse.isPresent()) {
          originalCallback.onResponse(cacheResponse.get());
          dispatchedCacheResult = true;
        } else if (cacheException.isPresent()) {
          dispatchedCacheResult = true;
        }
      }
      // Only send the network result after the cache result has been dispatched
      if (dispatchedCacheResult) {
        if (networkResponse.isPresent()) {
          originalCallback.onResponse(networkResponse.get());
          originalCallback.onCompleted();
        } else if (networkException.isPresent()) {
          originalCallback.onFailure(networkException.get());
        }
      }
    }
  }
}
