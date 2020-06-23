---
title: Caching response data
---

Apollo GraphQL client allows you to cache responses, making it suitable for use even while offline. The client can be configured with 3 levels of caching:

 - **HTTP Response Cache**: For caching raw http responses.
 - **Normalized Disk Cache**: Per node caching of responses in SQL. Persists normalized responses on disk so that they can used after process death. 
 - **Normalized InMemory Cache**: Optimized Guava memory cache for in memory caching as long as the App/Process is still alive.  

## Http Cache

To enable HTTP Cache support, add the dependency to your project's build.gradle file. The latest version is
[![Download](https://api.bintray.com/packages/apollographql/android/apollo/images/download.svg)](https://bintray.com/apollographql/android/apollo-http-cache/_latestVersion)

```kotlin:title=build.gradle
dependencies {
  implementation("com.apollographql.apollo:apollo-http-cache:x.y.z")
}
```

### Raw HTTP Response Cache

```kotlin:title=Kotlin
// Directory where cached responses will be stored
val file = File(cacheDir, "apolloCache")

// Size in bytes of the cache
val size: Long = 1024 * 1024

// Create the http response cache store
val cacheStore = DiskLruHttpCacheStore(file, size)

// Build the Apollo Client
val apolloClient = ApolloClient.builder()
    .serverUrl("/")
    .httpCache(ApolloHttpCache(cacheStore))
    .okHttpClient(okHttpClient)
    .build()
                   
// Control the cache policy  
val query = FeedQuery(limit = 10, type = FeedType.HOT)
val dataResponse = apolloClient.query(query)
    .httpCachePolicy(HttpCachePolicy.CACHE_FIRST)
    .toDeferred().await()
```

```java:title=Java
//Directory where cached responses will be stored
File file = new File(context.getApplicationContext().getFilesDir(), "apolloCache");

//Size in bytes of the cache
long size = 1024*1024;

//Create the http response cache store
DiskLruHttpCacheStore cacheStore = new DiskLruHttpCacheStore(file, size); 

//Build the Apollo Client
ApolloClient apolloClient = ApolloClient.builder()
  .serverUrl("/")
  .httpCache(new ApolloHttpCache(cacheStore))
  .okHttpClient(okHttpClient)
  .build();

apolloClient
  .query(
    FeedQuery.builder()
      .limit(10)
      .type(FeedType.HOT)
      .build()
  )
  .httpCachePolicy(HttpCachePolicy.CACHE_FIRST)
  .enqueue(new ApolloCall.Callback<FeedQuery.Data>() {

    @Override public void onResponse(@NotNull Response<FeedQuery.Data> dataResponse) {
      ...
    }

    @Override public void onFailure(@NotNull Throwable t) {
      ...
    }
  }); 
```

**IMPORTANT:** Caching is provided only for `query` operations. It isn't available for `mutation` operations.

There are four available cache policies `HttpCachePolicy`:

- `CACHE_ONLY` - Fetch a response from the cache only, ignoring the network. If the cached response doesn't exist or is expired, then
return an error.
- `NETWORK_ONLY` - Fetch a response from the network only, ignoring any cached responses.
- `CACHE_FIRST` - Fetch a response from the cache first. If the response doesn't exist or is expired, then fetch a response from the
network.
- `NETWORK_FIRST` - Fetch a response from the network first. If the network fails and the cached response isn't expired, then return cached
data instead.

For `CACHE_ONLY`, `CACHE_FIRST` and `NETWORK_FIRST` policies you can define the timeout after what cached response is treated as expired
and will be evicted from the http cache, `expireAfter(expireTimeout, timeUnit)`.`

## Normalized Disk Cache:

To enable Normalized Disk Cache support, add the dependency to your project's build.gradle file. The latest version is
[![Download](https://api.bintray.com/packages/apollographql/android/apollo/images/download.svg)](https://bintray.com/apollographql/android/apollo-normalized-cache-sqlite/_latestVersion)

```kotlin:title=build.gradle
dependencies {
  implementation("com.apollographql.apollo:apollo-normalized-cache-sqlite:x.y.z")
}
```                                                                   

### Usage

```kotlin:title=Kotlin             
// Create NormalizedCacheFactory
// Please note that if null is passed in as the name, you will get an in-memory SQLite database that 
// will not persist across restarts of the app.
val cacheFactory = SqlNormalizedCacheFactory(this, "github_cache")
val resolver: CacheKeyResolver = object : CacheKeyResolver() {
  override fun fromFieldRecordSet(field: ResponseField, recordSet: Map<String, Any>): CacheKey {
    return formatCacheKey(recordSet["id"] as String?)
  }

  override fun fromFieldArguments(field: ResponseField, variables: Operation.Variables): CacheKey {
    return formatCacheKey(field.resolveArgument("id", variables) as String?)
  }

  private fun formatCacheKey(id: String?) = when {
    id.isNullOrEmpty() -> CacheKey.NO_KEY
    else -> CacheKey.from(id)
  }
}

val apolloClient = ApolloClient.builder()
    .serverUrl("/")
    .normalizedCache(cacheFactory, resolver)
    .okHttpClient(okHttpClient)
    .build()
```

```java:title=Java
// Create NormalizedCacheFactory
// Please note that if null is passed in as the name, you will get an in-memory SQLite database that 
// will not persist across restarts of the app.
NormalizedCacheFactory cacheFactory = new SqlNormalizedCacheFactory(context, "db_name");

// Create the cache key resolver, this example works well when all types have globally unique ids.
CacheKeyResolver resolver =  new CacheKeyResolver() {
   @NotNull @Override
   public CacheKey fromFieldRecordSet(@NotNull ResponseField field, @NotNull Map<String, Object> recordSet) {
     return formatCacheKey((String) recordSet.get("id"));
   }
 
   @NotNull @Override
   public CacheKey fromFieldArguments(@NotNull ResponseField field, @NotNull Operation.Variables variables) {
     return formatCacheKey((String) field.resolveArgument("id", variables));
   }
 
   private CacheKey formatCacheKey(String id) {
     if (id == null || id.isEmpty()) {
       return CacheKey.NO_KEY;
     } else {
       return CacheKey.from(id);
     }
   }
};

//Build the Apollo Client
ApolloClient apolloClient = ApolloClient.builder()
  .serverUrl("/")
  .normalizedCache(cacheFactory, resolver)
  .okHttpClient(okHttpClient)
  .build();
```

## Normalized In-Memory Cache:

To enable Normalized In-Memory Cache support, add the dependency to your project's build.gradle file. The latest version is
[![Download](https://api.bintray.com/packages/apollographql/android/apollo/images/download.svg)](https://bintray.com/apollographql/android/apollo-normalized-cache/_latestVersion)

```kotlin:title=build.gradle
dependencies {
  implementation("com.apollographql.apollo:apollo-normalized-cache:x.y.z")
}
``` 

### Usage

```kotlin:title=Kotlin
// Create NormalizedCacheFactory
val cacheFactory = LruNormalizedCacheFactory(EvictionPolicy.builder().maxSizeBytes(10 * 1024).build())

// Build the Apollo Client
val apolloClient = ApolloClient.builder()
  .serverUrl("/")
  .normalizedCache(cacheFactory, resolver)
  .okHttpClient(okHttpClient)
  .build())
```

```java:title=Java
// Create NormalizedCacheFactory
NormalizedCacheFactory cacheFactory = new LruNormalizedCacheFactory(EvictionPolicy.builder().maxSizeBytes(10 * 1024).build());

// Build the Apollo Client
ApolloClient apolloClient = ApolloClient.builder()
  .serverUrl("/")
  .normalizedCache(cacheFactory, resolver)
  .okHttpClient(okHttpClient)
  .build();
```

## Chaining Caches:

You can use both a memory cache and disk cache, with a cache chain. Reads will read from the first cache hit in the chain. Writes will
propagate down the entire chain.

```kotlin:title=Kotlin

val sqlCacheFactory = SqlNormalizedCacheFactory(context, "db_name")
val memoryFirstThenSqlCacheFactory = LruNormalizedCacheFactory(
    EvictionPolicy.builder().maxSizeBytes(10 * 1024).build()
).chain(sqlCacheFactory)

```

```java:title=Java

NormalizedCacheFactory sqlCacheFactory = new SqlNormalizedCacheFactory(context, "db_name");
NormalizedCacheFactory memoryFirstThenSqlCacheFactory = new LruNormalizedCacheFactory(
  EvictionPolicy.builder().maxSizeBytes(10 * 1024).build()
).chain(sqlCacheFactory);

```

## Examples

[Java Sample](https://github.com/apollographql/apollo-android/tree/master/samples/java-sample) has an example of chained cache of in-memory
and SQLite caches.

[Kotlin Sample](https://github.com/apollographql/apollo-android/tree/master/samples/kotlin-sample) has an example of SQLite cache usage
written in Kotlin.
 
For other concrete examples of using response caches, please see the following tests in their respective modules:
`SqlNormalizedCacheTest`, `LruNormalizedCacheTest`. 
