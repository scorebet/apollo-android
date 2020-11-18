package com.apollographql.apollo.cache.normalized.internal

import com.apollographql.apollo.api.GraphqlFragment
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.api.Response.Companion.builder
import com.apollographql.apollo.api.internal.ResponseFieldMapper
import com.apollographql.apollo.cache.CacheHeaders
import com.apollographql.apollo.cache.normalized.ApolloStore
import com.apollographql.apollo.cache.normalized.ApolloStoreOperation
import com.apollographql.apollo.cache.normalized.ApolloStoreOperation.Companion.emptyOperation
import com.apollographql.apollo.cache.normalized.CacheKey
import com.apollographql.apollo.cache.normalized.CacheKeyResolver
import com.apollographql.apollo.cache.normalized.NormalizedCache
import com.apollographql.apollo.cache.normalized.Record
import java.util.UUID

/**
 * An alternative to RealApolloStore for when a no-operation cache is needed.
 */
class NoOpApolloStore : ApolloStore, ReadableStore, WriteableStore {
  override fun merge(recordCollection: Collection<Record>, cacheHeaders: CacheHeaders): Set<String> {
    return emptySet()
  }

  override fun merge(record: Record, cacheHeaders: CacheHeaders): Set<String> {
    return emptySet()
  }

  override fun read(key: String, cacheHeaders: CacheHeaders): Record? {
    return null
  }

  override fun read(keys: Collection<String>, cacheHeaders: CacheHeaders): Collection<Record> {
    return emptySet()
  }

  override fun subscribe(subscriber: ApolloStore.RecordChangeSubscriber) {}
  override fun unsubscribe(subscriber: ApolloStore.RecordChangeSubscriber) {}
  override fun publish(keys: Set<String>) {}
  override fun clearAll(): ApolloStoreOperation<Boolean> {
    return emptyOperation(java.lang.Boolean.FALSE)
  }

  override fun remove(cacheKey: CacheKey, cascade: Boolean): ApolloStoreOperation<Boolean> {
    return emptyOperation(java.lang.Boolean.FALSE)
  }

  override fun remove(cacheKey: CacheKey): ApolloStoreOperation<Boolean> {
    return emptyOperation(java.lang.Boolean.FALSE)
  }

  override fun remove(cacheKeys: List<CacheKey>): ApolloStoreOperation<Int> {
    return emptyOperation(0)
  }

  override fun networkResponseNormalizer(): ResponseNormalizer<Map<String, Any>> {
    return ResponseNormalizer.NO_OP_NORMALIZER as ResponseNormalizer<Map<String, Any>>
  }

  override fun cacheResponseNormalizer(): ResponseNormalizer<Record> {
    return ResponseNormalizer.NO_OP_NORMALIZER as ResponseNormalizer<Record>
  }

  override fun <R> readTransaction(transaction: Transaction<ReadableStore, R>): R {
    return transaction.execute(this)!!
  }

  override fun <R> writeTransaction(transaction: Transaction<WriteableStore, R>): R {
    return transaction.execute(this)!!
  }

  override fun normalizedCache(): NormalizedCache {
    error("Cannot get normalizedCache: no cache configured")
  }

  override fun cacheKeyResolver(): CacheKeyResolver {
    error("Cannot get cacheKeyResolver: no cache configured")
  }

  override fun <D : Operation.Data, T, V : Operation.Variables> read(
      operation: Operation<D, T, V>): ApolloStoreOperation<T> {
    error("Cannot read operation: no cache configured")
  }

  override fun <D : Operation.Data, T, V : Operation.Variables> read(
      operation: Operation<D, T, V>, responseFieldMapper: ResponseFieldMapper<D>,
      responseNormalizer: ResponseNormalizer<Record>, cacheHeaders: CacheHeaders): ApolloStoreOperation<Response<T>> {
    // This is called in the default path when no cache is configured, do not trigger an error
    // Instead return an empty response. This will be seen as a cache MISS and the request will go to the network.
    return emptyOperation(Response.builder<T>(operation).build())
  }

  override fun <F : GraphqlFragment> read(fieldMapper: ResponseFieldMapper<F>,
                                           cacheKey: CacheKey, variables: Operation.Variables): ApolloStoreOperation<F> {
    error("Cannot read fragment: no cache configured")
  }

  override fun <D : Operation.Data, T, V : Operation.Variables> write(
      operation: Operation<D, T, V>, operationData: D): ApolloStoreOperation<Set<String>> {
    // Should we throw here instead?
    return emptyOperation(emptySet())
  }

  override fun <D : Operation.Data, T, V : Operation.Variables> writeAndPublish(
      operation: Operation<D, T, V>, operationData: D): ApolloStoreOperation<Boolean> {
    // Should we throw here instead?
    return emptyOperation(false)
  }

  override fun write(fragment: GraphqlFragment, cacheKey: CacheKey,
                     variables: Operation.Variables): ApolloStoreOperation<Set<String>> {
    // Should we throw here instead?
    return emptyOperation(emptySet())
  }

  override fun writeAndPublish(fragment: GraphqlFragment, cacheKey: CacheKey,
                               variables: Operation.Variables): ApolloStoreOperation<Boolean> {
    // Should we throw here instead?
    return emptyOperation(false)
  }

  override fun <D : Operation.Data, T, V : Operation.Variables> writeOptimisticUpdates(operation: Operation<D, T, V>, operationData: D, mutationId: UUID): ApolloStoreOperation<Set<String>> {
    // Should we throw here instead?
    return emptyOperation(emptySet())
  }

  override fun <D : Operation.Data, T, V : Operation.Variables> writeOptimisticUpdatesAndPublish(operation: Operation<D, T, V>, operationData: D,
                                                                                                   mutationId: UUID): ApolloStoreOperation<Boolean> {
    // Should we throw here instead?
    return emptyOperation(java.lang.Boolean.FALSE)
  }

  override fun rollbackOptimisticUpdatesAndPublish(mutationId: UUID): ApolloStoreOperation<Boolean> {
    // Should we throw here instead?
    return emptyOperation(java.lang.Boolean.FALSE)
  }

  override fun rollbackOptimisticUpdates(mutationId: UUID): ApolloStoreOperation<Set<String>> {
    // Should we throw here instead?
    return emptyOperation(emptySet())
  }
}