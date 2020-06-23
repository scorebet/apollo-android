package com.apollographql.apollo.cache.normalized.sql

import com.apollographql.apollo.cache.normalized.NormalizedCacheFactory
import com.apollographql.apollo.cache.normalized.RecordFieldJsonAdapter
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.drivers.native.NativeSqliteDriver

actual class SqlNormalizedCacheFactory internal actual constructor(
    driver: SqlDriver
) : NormalizedCacheFactory<SqlNormalizedCache>() {

  constructor() : this("apollo.db")

  constructor(name: String) : this(NativeSqliteDriver(ApolloDatabase.Schema, name))

  private val apolloDatabase = ApolloDatabase(driver)

  override fun create(recordFieldAdapter: RecordFieldJsonAdapter) =
      SqlNormalizedCache(recordFieldAdapter, apolloDatabase.cacheQueries)
}
