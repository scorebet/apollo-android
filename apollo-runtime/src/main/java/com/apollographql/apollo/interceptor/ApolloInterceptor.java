package com.apollographql.apollo.interceptor;

import com.apollographql.apollo.api.Operation;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.api.internal.Optional;
import com.apollographql.apollo.cache.CacheHeaders;
import com.apollographql.apollo.cache.normalized.Record;
import com.apollographql.apollo.exception.ApolloException;
import com.apollographql.apollo.request.RequestHeaders;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.jetbrains.annotations.NotNull;

import static com.apollographql.apollo.api.internal.Utils.checkNotNull;

/**
 * ApolloInterceptor is responsible for observing and modifying the requests going out and the corresponding responses
 * coming back in. Typical responsibilities include adding or removing headers from the request or response objects,
 * transforming the returned responses from one type to another, etc.
 */
public interface ApolloInterceptor {
  /**
   * Intercepts the outgoing request and performs non blocking operations on the request or the response returned by the
   * next set of interceptors in the chain.
   *
   * @param request    outgoing request object.
   * @param chain      the ApolloInterceptorChain object containing the next set of interceptors.
   * @param dispatcher the Executor which dispatches the non blocking operations on the request/response.
   * @param callBack   the Callback which will handle the interceptor's response or failure exception.
   */
  void interceptAsync(@NotNull InterceptorRequest request, @NotNull ApolloInterceptorChain chain,
      @NotNull Executor dispatcher, @NotNull CallBack callBack);

  /**
   * Disposes of the resources which are no longer required.
   *
   * <p>A use case for this method call would be when an {@link com.apollographql.apollo.ApolloCall} needs to be
   * cancelled and resources need to be disposed of. </p>
   */
  void dispose();

  /**
   * Handles the responses returned by {@link ApolloInterceptor}
   */
  interface CallBack {

    /**
     * Gets called when the interceptor returns a response after successfully performing operations on the
     * request/response. May be called multiple times.
     *
     * @param response The response returned by the interceptor.
     */
    void onResponse(@NotNull InterceptorResponse response);

    /**
     * Called when interceptor starts fetching response from source type
     *
     * @param sourceType type of source been used to fetch response from
     */
    void onFetch(FetchSourceType sourceType);

    /**
     * Gets called when an unexpected exception occurs while performing operations on the request or processing the
     * response returned by the next set of interceptors. Will be called at most once.
     */
    void onFailure(@NotNull ApolloException e);

    /**
     * Called after the last call to {@link #onResponse}. Do not call after {@link #onFailure(ApolloException)}.
     */
    void onCompleted();
  }

  /**
   * Fetch source type
   */
  enum FetchSourceType {
    /**
     * Response is fetched from the cache (SQLite or memory or both)
     */
    CACHE,
    /**
     * Response is fetched from the network
     */
    NETWORK
  }

  /**
   * InterceptorResponse class represents the response returned by the {@link ApolloInterceptor}.
   */
  final class InterceptorResponse {
    public final Optional<okhttp3.Response> httpResponse;
    public final Optional<Response> parsedResponse;
    public final Optional<Collection<Record>> cacheRecords;

    public InterceptorResponse(okhttp3.Response httpResponse) {
      this(httpResponse, null, null);
    }

    public InterceptorResponse(okhttp3.Response httpResponse, Response parsedResponse,
        Collection<Record> cacheRecords) {
      this.httpResponse = Optional.fromNullable(httpResponse);
      this.parsedResponse = Optional.fromNullable(parsedResponse);
      this.cacheRecords = Optional.fromNullable(cacheRecords);
    }
  }

  /**
   * Request to be proceed with {@link ApolloInterceptor}
   */
  final class InterceptorRequest {
    public final UUID uniqueId = UUID.randomUUID();
    public final Operation operation;
    public final CacheHeaders cacheHeaders;
    public final RequestHeaders requestHeaders;
    public final boolean fetchFromCache;
    public final Optional<Operation.Data> optimisticUpdates;
    public final boolean sendQueryDocument;
    public final boolean useHttpGetMethodForQueries;
    public final boolean autoPersistQueries;

    InterceptorRequest(Operation operation, CacheHeaders cacheHeaders, RequestHeaders requestHeaders,
        Optional<Operation.Data> optimisticUpdates, boolean fetchFromCache,
        boolean sendQueryDocument, boolean useHttpGetMethodForQueries, boolean autoPersistQueries) {
      this.operation = operation;
      this.cacheHeaders = cacheHeaders;
      this.requestHeaders = requestHeaders;
      this.optimisticUpdates = optimisticUpdates;
      this.fetchFromCache = fetchFromCache;
      this.sendQueryDocument = sendQueryDocument;
      this.useHttpGetMethodForQueries = useHttpGetMethodForQueries;
      this.autoPersistQueries = autoPersistQueries;
    }

    public Builder toBuilder() {
      return new Builder(operation)
          .cacheHeaders(cacheHeaders)
          .requestHeaders(requestHeaders)
          .fetchFromCache(fetchFromCache)
          .optimisticUpdates(optimisticUpdates.orNull())
          .sendQueryDocument(sendQueryDocument)
          .useHttpGetMethodForQueries(useHttpGetMethodForQueries)
          .autoPersistQueries(autoPersistQueries);
    }

    public static Builder builder(@NotNull Operation operation) {
      return new Builder(operation);
    }

    public static final class Builder {
      private final Operation operation;
      private CacheHeaders cacheHeaders = CacheHeaders.NONE;
      private RequestHeaders requestHeaders = RequestHeaders.NONE;
      private boolean fetchFromCache;
      private Optional<Operation.Data> optimisticUpdates = Optional.absent();
      private boolean sendQueryDocument = true;
      private boolean useHttpGetMethodForQueries;
      private boolean autoPersistQueries;

      Builder(@NotNull Operation operation) {
        this.operation = checkNotNull(operation, "operation == null");
      }

      public Builder cacheHeaders(@NotNull CacheHeaders cacheHeaders) {
        this.cacheHeaders = checkNotNull(cacheHeaders, "cacheHeaders == null");
        return this;
      }

      public Builder requestHeaders(@NotNull RequestHeaders requestHeaders) {
        this.requestHeaders = checkNotNull(requestHeaders, "requestHeaders == null");
        return this;
      }

      public Builder fetchFromCache(boolean fetchFromCache) {
        this.fetchFromCache = fetchFromCache;
        return this;
      }

      public Builder optimisticUpdates(Operation.Data optimisticUpdates) {
        this.optimisticUpdates = Optional.fromNullable(optimisticUpdates);
        return this;
      }

      public Builder optimisticUpdates(@NotNull Optional<Operation.Data> optimisticUpdates) {
        this.optimisticUpdates = checkNotNull(optimisticUpdates, "optimisticUpdates == null");
        return this;
      }

      public Builder sendQueryDocument(boolean sendQueryDocument) {
        this.sendQueryDocument = sendQueryDocument;
        return this;
      }

      public Builder useHttpGetMethodForQueries(boolean useHttpGetMethodForQueries) {
        this.useHttpGetMethodForQueries = useHttpGetMethodForQueries;
        return this;
      }

      public Builder autoPersistQueries(boolean autoPersistQueries) {
        this.autoPersistQueries = autoPersistQueries;
        return this;
      }

      public InterceptorRequest build() {
        return new InterceptorRequest(operation, cacheHeaders, requestHeaders, optimisticUpdates,
            fetchFromCache, sendQueryDocument, useHttpGetMethodForQueries, autoPersistQueries);
      }
    }
  }
}
