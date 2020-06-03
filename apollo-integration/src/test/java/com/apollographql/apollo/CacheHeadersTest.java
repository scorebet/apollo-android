package com.apollographql.apollo;

import com.apollographql.apollo.api.Input;
import com.apollographql.apollo.cache.ApolloCacheHeaders;
import com.apollographql.apollo.cache.CacheHeaders;
import com.apollographql.apollo.cache.normalized.CacheKey;
import com.apollographql.apollo.cache.normalized.NormalizedCache;
import com.apollographql.apollo.cache.normalized.NormalizedCacheFactory;
import com.apollographql.apollo.cache.normalized.Record;
import com.apollographql.apollo.cache.normalized.RecordFieldJsonAdapter;
import com.apollographql.apollo.exception.ApolloException;
import com.apollographql.apollo.integration.normalizer.HeroAndFriendsNamesQuery;
import com.apollographql.apollo.integration.normalizer.type.Episode;
import com.apollographql.apollo.rx2.Rx2Apollo;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Collections.emptySet;

public class CacheHeadersTest {

  @Rule public final MockWebServer server = new MockWebServer();

  @Test @SuppressWarnings("CheckReturnValue") public void testHeadersReceived() throws ApolloException, IOException {
    final AtomicBoolean hasHeader = new AtomicBoolean();
    final NormalizedCache normalizedCache = new NormalizedCache() {
      @Nullable @Override public Record loadRecord(@NotNull String key, @NotNull CacheHeaders cacheHeaders) {
        hasHeader.set(cacheHeaders.hasHeader(ApolloCacheHeaders.DO_NOT_STORE));
        return null;
      }

      @NotNull @Override public Set<String> merge(@NotNull Record record, @NotNull CacheHeaders cacheHeaders) {
        hasHeader.set(cacheHeaders.hasHeader(ApolloCacheHeaders.DO_NOT_STORE));
        return emptySet();
      }

      @Override public void clearAll() {
      }

      @Override public boolean remove(@NotNull CacheKey cacheKey, boolean cascade) {
        return false;
      }

      @NotNull @Override
      protected Set<String> performMerge(@NotNull Record apolloRecord, @NotNull CacheHeaders cacheHeaders) {
        return emptySet();
      }
    };

    final NormalizedCacheFactory<NormalizedCache> cacheFactory = new NormalizedCacheFactory<NormalizedCache>() {
      @Override public NormalizedCache create(RecordFieldJsonAdapter recordFieldAdapter) {
        return normalizedCache;
      }
    };

    ApolloClient apolloClient = ApolloClient.builder()
        .normalizedCache(cacheFactory, new IdFieldCacheKeyResolver())
        .serverUrl(server.url("/"))
        .okHttpClient(new OkHttpClient.Builder().dispatcher(new Dispatcher(Utils.INSTANCE.immediateExecutorService())).build())
        .dispatcher(Utils.INSTANCE.immediateExecutor())
        .build();

    server.enqueue(mockResponse("HeroAndFriendsNameResponse.json"));
    CacheHeaders cacheHeaders = CacheHeaders.builder().addHeader(ApolloCacheHeaders.DO_NOT_STORE, "true").build();
    Rx2Apollo.from(apolloClient.query(new HeroAndFriendsNamesQuery(Input.fromNullable(Episode.NEWHOPE)))
        .cacheHeaders(cacheHeaders))
        .test();
    assertThat(hasHeader.get()).isTrue();
  }

  @Test @SuppressWarnings("CheckReturnValue") public void testDefaultHeadersReceived() throws Exception {
    final AtomicBoolean hasHeader = new AtomicBoolean();
    final NormalizedCache normalizedCache = new NormalizedCache() {
      @Nullable @Override public Record loadRecord(@NotNull String key, @NotNull CacheHeaders cacheHeaders) {
        hasHeader.set(cacheHeaders.hasHeader(ApolloCacheHeaders.DO_NOT_STORE));
        return null;
      }

      @NotNull @Override public Set<String> merge(@NotNull Record record, @NotNull CacheHeaders cacheHeaders) {
        hasHeader.set(cacheHeaders.hasHeader(ApolloCacheHeaders.DO_NOT_STORE));
        return emptySet();
      }

      @Override public void clearAll() {
      }

      @Override public boolean remove(@NotNull CacheKey cacheKey, boolean cascade) {
        return false;
      }

      @NotNull @Override
      protected Set<String> performMerge(@NotNull Record apolloRecord, @NotNull CacheHeaders cacheHeaders) {
        return emptySet();
      }
    };

    final NormalizedCacheFactory<NormalizedCache> cacheFactory = new NormalizedCacheFactory<NormalizedCache>() {
      @Override public NormalizedCache create(RecordFieldJsonAdapter recordFieldAdapter) {
        return normalizedCache;
      }
    };

    CacheHeaders cacheHeaders = CacheHeaders.builder().addHeader(ApolloCacheHeaders.DO_NOT_STORE, "true").build();

    ApolloClient apolloClient = ApolloClient.builder()
        .normalizedCache(cacheFactory, new IdFieldCacheKeyResolver())
        .serverUrl(server.url("/"))
        .okHttpClient(new OkHttpClient.Builder().dispatcher(new Dispatcher(Utils.INSTANCE.immediateExecutorService())).build())
        .dispatcher(Utils.INSTANCE.immediateExecutor())
        .defaultCacheHeaders(cacheHeaders)
        .build();

    server.enqueue(mockResponse("HeroAndFriendsNameResponse.json"));
    Rx2Apollo.from(apolloClient.query(new HeroAndFriendsNamesQuery(Input.fromNullable(Episode.NEWHOPE)))
        .cacheHeaders(cacheHeaders))
        .test();
    assertThat(hasHeader.get()).isTrue();
  }

  private MockResponse mockResponse(String fileName) throws IOException {
    return new MockResponse().setChunkedBody(Utils.INSTANCE.readFileToString(getClass(), "/" + fileName), 32);
  }

}
