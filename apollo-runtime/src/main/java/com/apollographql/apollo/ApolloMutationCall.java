package com.apollographql.apollo;

import com.apollographql.apollo.api.Mutation;
import com.apollographql.apollo.api.OperationName;
import com.apollographql.apollo.api.Query;
import com.apollographql.apollo.cache.CacheHeaders;
import com.apollographql.apollo.fetcher.ResponseFetcher;
import com.apollographql.apollo.request.RequestHeaders;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A call prepared to execute GraphQL mutation operation.
 */
public interface ApolloMutationCall<T> extends ApolloCall<T> {

  /**
   * Sets the {@link CacheHeaders} to use for this call. {@link com.apollographql.apollo.interceptor.FetchOptions} will
   * be configured with this headers, and will be accessible from the {@link ResponseFetcher} used for this call.
   *
   * Deprecated, use {@link #toBuilder()} to mutate the ApolloCall
   *
   * @param cacheHeaders the {@link CacheHeaders} that will be passed with records generated from this request to {@link
   *                     com.apollographql.apollo.cache.normalized.NormalizedCache}. Standardized cache headers are
   *                     defined in {@link com.apollographql.apollo.cache.ApolloCacheHeaders}.
   * @return The ApolloCall object with the provided {@link CacheHeaders}.
   */
  @Deprecated @NotNull @Override ApolloMutationCall<T> cacheHeaders(@NotNull CacheHeaders cacheHeaders);

  /**
   * <p>Sets a list of {@link ApolloQueryWatcher} query names to be re-fetched once this mutation completed.</p>
   *
   * @param operationNames array of {@link OperationName} query names to be re-fetched
   * @return {@link ApolloMutationCall} that will trigger re-fetching provided queries
   */
  @Deprecated @NotNull ApolloMutationCall<T> refetchQueries(@NotNull OperationName... operationNames);

  /**
   * <p>Sets a list of {@link Query} to be re-fetched once this mutation completed.</p>
   *
   * @param queries array of {@link Query} to be re-fetched
   * @return {@link ApolloMutationCall} that will trigger re-fetching provided queries
   */
  @Deprecated @NotNull ApolloMutationCall<T> refetchQueries(@NotNull Query... queries);

  /**
   * Sets the {@link RequestHeaders} to use for this call. These headers will be added to the HTTP request when
   * it is issued. These headers will be applied after any headers applied by application-level interceptors
   * and will override those if necessary.
   *
   * @param requestHeaders The {@link RequestHeaders} to use for this request.
   * @return The ApolloCall object with the provided {@link RequestHeaders}.
   */
  @Deprecated @NotNull ApolloMutationCall<T> requestHeaders(@NotNull RequestHeaders requestHeaders);

  @Deprecated @NotNull @Override ApolloMutationCall<T> clone();

  @NotNull @Override ApolloMutationCall.Builder<T> toBuilder();

  interface Builder<T> extends ApolloCall.Builder<T> {
    @NotNull @Override ApolloMutationCall<T> build();

    @NotNull @Override Builder<T> cacheHeaders(@NotNull CacheHeaders cacheHeaders);

    /**
     * <p>Sets a list of {@link ApolloQueryWatcher} query names to be re-fetched once this mutation completed.</p>
     *
     * @param operationNames array of {@link OperationName} query names to be re-fetched
     * @return The Builder
     */
    @NotNull Builder<T> refetchQueryNames(@NotNull List<OperationName> operationNames);

    /**
     * <p>Sets a list of {@link Query} to be re-fetched once this mutation completed.</p>
     *
     * @param queries array of {@link Query} to be re-fetched
     * @return The Builder
     */
    @NotNull Builder<T> refetchQueries(@NotNull List<Query> queries);

    /**
     * Sets the {@link RequestHeaders} to use for this call. These headers will be added to the HTTP request when
     * it is issued. These headers will be applied after any headers applied by application-level interceptors
     * and will override those if necessary.
     *
     * @param requestHeaders The {@link RequestHeaders} to use for this request.
     * @return The Builder
     */
    @NotNull Builder<T> requestHeaders(@NotNull RequestHeaders requestHeaders);

  }
  /**
   * Factory for creating {@link ApolloMutationCall} calls.
   */
  interface Factory {
    /**
     * Creates and prepares a new {@link ApolloMutationCall} call.
     *
     * @param mutation the {@link Mutation} which needs to be performed
     * @return prepared {@link ApolloMutationCall} call to be executed at some point in the future
     */
    <D extends Mutation.Data, T, V extends Mutation.Variables> ApolloMutationCall<T> mutate(
        @NotNull Mutation<D, T, V> mutation);

    /**
     * <p>Creates and prepares a new {@link ApolloMutationCall} call with optimistic updates.</p>
     *
     * Provided optimistic updates will be stored in {@link com.apollographql.apollo.cache.normalized.ApolloStore}
     * immediately before mutation execution. Any {@link ApolloQueryWatcher} dependent on the changed cache records will
     * be re-fetched.
     *
     * @param mutation              the {@link Mutation} which needs to be performed
     * @param withOptimisticUpdates optimistic updates for this mutation
     * @return prepared {@link ApolloMutationCall} call to be executed at some point in the future
     */
    <D extends Mutation.Data, T, V extends Mutation.Variables> ApolloMutationCall<T> mutate(
        @NotNull Mutation<D, T, V> mutation, @NotNull D withOptimisticUpdates);
  }
}
