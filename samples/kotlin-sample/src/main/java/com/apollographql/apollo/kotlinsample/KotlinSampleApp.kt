package com.apollographql.apollo.kotlinsample

import android.app.Application
import android.util.Log
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.ResponseField
import com.apollographql.apollo.api.cache.http.HttpCachePolicy
import com.apollographql.apollo.cache.http.ApolloHttpCache
import com.apollographql.apollo.cache.http.DiskLruHttpCacheStore
import com.apollographql.apollo.cache.normalized.CacheKey
import com.apollographql.apollo.cache.normalized.CacheKeyResolver
import com.apollographql.apollo.cache.normalized.sql.SqlNormalizedCacheFactory
import com.apollographql.apollo.kotlinsample.data.ApolloCallbackService
import com.apollographql.apollo.kotlinsample.data.ApolloCoroutinesService
import com.apollographql.apollo.kotlinsample.data.ApolloRxService
import com.apollographql.apollo.kotlinsample.data.ApolloWatcherService
import com.apollographql.apollo.kotlinsample.data.GitHubDataSource
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File

class KotlinSampleApp : Application() {
  private val baseUrl = "https://api.github.com/graphql"
  private val apolloClient: ApolloClient by lazy {
    val logInterceptor = HttpLoggingInterceptor(
        object : HttpLoggingInterceptor.Logger {
          override fun log(message: String) {
            Log.d("OkHttp", message)
          }
        }
    ).apply { level = HttpLoggingInterceptor.Level.BODY }

    val okHttpClient = OkHttpClient.Builder()
        .addNetworkInterceptor { chain ->
          val request = chain.request().newBuilder()
              .addHeader("Authorization", "bearer ${BuildConfig.GITHUB_OAUTH_TOKEN}")
              .build()

          chain.proceed(request)
        }
        .addInterceptor(logInterceptor)
        .build()

    val sqlNormalizedCacheFactory = SqlNormalizedCacheFactory(this, "github_cache")
    val cacheKeyResolver = object : CacheKeyResolver() {
      override fun fromFieldRecordSet(field: ResponseField, recordSet: Map<String, Any>): CacheKey {
        return if (recordSet["__typename"] == "Repository") {
          CacheKey(recordSet["id"] as String)
        } else {
          CacheKey.NO_KEY
        }
      }

      override fun fromFieldArguments(field: ResponseField, variables: Operation.Variables): CacheKey {
        return CacheKey.NO_KEY
      }
    }

    // Create the http response cache store
    val cacheStore = DiskLruHttpCacheStore(File(cacheDir, "apolloCache"), 1024 * 1024)

    ApolloClient.builder()
        .serverUrl(baseUrl)
        .normalizedCache(sqlNormalizedCacheFactory, cacheKeyResolver)
        .httpCache(ApolloHttpCache(cacheStore))
        .defaultHttpCachePolicy(HttpCachePolicy.CACHE_FIRST)
        .okHttpClient(okHttpClient)
        .build()
  }

  /**
   * Builds an implementation of [GitHubDataSource]. To configure which one is returned, just comment out the appropriate
   * lines.
   *
   * @param[serviceTypes] Modify the type of service we use, if we want. To use the same thing across the board simply update
   * it here.
   */
  fun getDataSource(serviceTypes: ServiceTypes = ServiceTypes.CALLBACK): GitHubDataSource {
    return when (serviceTypes) {
      ServiceTypes.CALLBACK -> ApolloCallbackService(apolloClient)
      ServiceTypes.RX_JAVA -> ApolloRxService(apolloClient)
      ServiceTypes.COROUTINES -> ApolloCoroutinesService(apolloClient)
      ServiceTypes.WATCHER -> ApolloWatcherService(apolloClient)
    }
  }

  enum class ServiceTypes {
    CALLBACK,
    RX_JAVA,
    COROUTINES,
    WATCHER
  }
}
