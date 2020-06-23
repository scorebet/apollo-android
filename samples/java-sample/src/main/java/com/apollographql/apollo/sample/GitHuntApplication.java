package com.apollographql.apollo.sample;

import android.app.Application;
import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.api.Operation;
import com.apollographql.apollo.api.ResponseField;
import com.apollographql.apollo.cache.normalized.CacheKey;
import com.apollographql.apollo.cache.normalized.CacheKeyResolver;
import com.apollographql.apollo.cache.normalized.NormalizedCacheFactory;
import com.apollographql.apollo.cache.normalized.lru.EvictionPolicy;
import com.apollographql.apollo.cache.normalized.lru.LruNormalizedCacheFactory;
import com.apollographql.apollo.cache.normalized.sql.SqlNormalizedCacheFactory;
import com.apollographql.apollo.subscription.WebSocketSubscriptionTransport;
import okhttp3.OkHttpClient;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class GitHuntApplication extends Application {

//  private static final String BASE_URL = "http://10.0.2.2:3010/graphql/";
//  private static final String SUBSCRIPTION_BASE_URL = "ws://10.0.2.2:3010/subscriptions";
  private static final String BASE_URL = "https://api.githunt.com/graphql";
  private static final String SUBSCRIPTION_BASE_URL = "wss://api.githunt.com/subscriptions";

  private static final String SQL_CACHE_NAME = "githuntdb";
  private ApolloClient apolloClient;

  @Override public void onCreate() {
    super.onCreate();
    OkHttpClient okHttpClient = new OkHttpClient.Builder()
        .build();

    NormalizedCacheFactory normalizedCacheFactory = new LruNormalizedCacheFactory(EvictionPolicy.NO_EVICTION)
        .chain(new SqlNormalizedCacheFactory(this, SQL_CACHE_NAME));

    CacheKeyResolver cacheKeyResolver = new CacheKeyResolver() {
      @NotNull @Override
      public CacheKey fromFieldRecordSet(@NotNull ResponseField field, @NotNull Map<String, Object> recordSet) {
        String typeName = (String) recordSet.get("__typename");
        if ("User".equals(typeName)) {
          String userKey = typeName + "." + recordSet.get("login");
          return CacheKey.from(userKey);
        }
        if (recordSet.containsKey("id")) {
          String typeNameAndIDKey = recordSet.get("__typename") + "." + recordSet.get("id");
          return CacheKey.from(typeNameAndIDKey);
        }
        return CacheKey.NO_KEY;
      }

      // Use this resolver to customize the key for fields with variables: eg entry(repoFullName: $repoFullName).
      // This is useful if you want to make query to be able to resolved, even if it has never been run before.
      @NotNull @Override
      public CacheKey fromFieldArguments(@NotNull ResponseField field, @NotNull Operation.Variables variables) {
        return CacheKey.NO_KEY;
      }
    };

    apolloClient = ApolloClient.builder()
        .serverUrl(BASE_URL)
        .okHttpClient(okHttpClient)
        .normalizedCache(normalizedCacheFactory, cacheKeyResolver)
        .subscriptionTransportFactory(new WebSocketSubscriptionTransport.Factory(SUBSCRIPTION_BASE_URL, okHttpClient))
        .build();
  }

  public ApolloClient apolloClient() {
    return apolloClient;
  }

}
