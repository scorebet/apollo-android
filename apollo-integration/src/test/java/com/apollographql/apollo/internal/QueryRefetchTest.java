package com.apollographql.apollo.internal;


import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.ApolloQueryWatcher;
import com.apollographql.apollo.IdFieldCacheKeyResolver;
import com.apollographql.apollo.Utils;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.cache.normalized.lru.EvictionPolicy;
import com.apollographql.apollo.cache.normalized.lru.LruNormalizedCacheFactory;
import com.apollographql.apollo.exception.ApolloException;
import com.apollographql.apollo.integration.normalizer.CreateReviewMutation;
import com.apollographql.apollo.integration.normalizer.ReviewsByEpisodeQuery;
import com.apollographql.apollo.integration.normalizer.type.ColorInput;
import com.apollographql.apollo.integration.normalizer.type.Episode;
import com.apollographql.apollo.integration.normalizer.type.ReviewInput;
import com.apollographql.apollo.rx2.Rx2Apollo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import org.jetbrains.annotations.NotNull;

import io.reactivex.functions.Predicate;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockWebServer;

import static com.apollographql.apollo.fetcher.ApolloResponseFetchers.CACHE_ONLY;
import static com.apollographql.apollo.fetcher.ApolloResponseFetchers.NETWORK_FIRST;
import static com.google.common.truth.Truth.assertThat;

public class QueryRefetchTest {
  private ApolloClient apolloClient;
  private MockWebServer server;

  @Before public void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
    OkHttpClient okHttpClient = new OkHttpClient.Builder()
        .dispatcher(new Dispatcher(Utils.INSTANCE.immediateExecutorService()))
        .build();

    apolloClient = ApolloClient.builder()
        .serverUrl(server.url("/"))
        .dispatcher(Utils.INSTANCE.immediateExecutor())
        .okHttpClient(okHttpClient)
        .normalizedCache(new LruNormalizedCacheFactory(EvictionPolicy.NO_EVICTION), new IdFieldCacheKeyResolver())
        .build();
  }

  @After public void tearDown() {
    try {
      server.shutdown();
    } catch (IOException ignored) {
    }
  }

  @Test @SuppressWarnings("CheckReturnValue") public void refetchNoPreCachedQuery() throws Exception {
    CreateReviewMutation mutation = new CreateReviewMutation(
        Episode.EMPIRE,
        ReviewInput.builder().stars(5).commentary("Awesome").favoriteColor(ColorInput.builder().build()).build()
    );

    server.enqueue(Utils.INSTANCE.mockResponse("CreateReviewResponse.json"));
    server.enqueue(Utils.INSTANCE.mockResponse("ReviewsEmpireEpisodeResponse.json"));

    RealApolloCall call = (RealApolloCall) apolloClient.mutate(mutation).refetchQueries(new ReviewsByEpisodeQuery(Episode.EMPIRE));
    Rx2Apollo
        .from(call)
        .test();

    assertThat(server.getRequestCount()).isEqualTo(2);
    Utils.INSTANCE.assertResponse(
        apolloClient.query(new ReviewsByEpisodeQuery(Episode.EMPIRE)).responseFetcher(CACHE_ONLY),
        new Predicate<Response<ReviewsByEpisodeQuery.Data>>() {
          @Override public boolean test(Response<ReviewsByEpisodeQuery.Data> response) throws Exception {
            assertThat(response.data().reviews()).hasSize(3);
            assertThat(response.data().reviews().get(2).stars()).isEqualTo(5);
            assertThat(response.data().reviews().get(2).commentary()).isEqualTo("Amazing");
            return true;
          }
        }
    );
  }

  @Test @SuppressWarnings("CheckReturnValue") public void refetchPreCachedQuery() throws Exception {
    Utils.INSTANCE.enqueueAndAssertResponse(
        server,
        "ReviewsEmpireEpisodeResponse.json",
        apolloClient.query(new ReviewsByEpisodeQuery(Episode.EMPIRE)).responseFetcher(NETWORK_FIRST),
        new Predicate<Response<ReviewsByEpisodeQuery.Data>>() {
          @Override public boolean test(Response<ReviewsByEpisodeQuery.Data> response) throws Exception {
            return !response.hasErrors();
          }
        }
    );

    Utils.INSTANCE.assertResponse(
        apolloClient.query(new ReviewsByEpisodeQuery(Episode.EMPIRE)).responseFetcher(CACHE_ONLY),
        new Predicate<Response<ReviewsByEpisodeQuery.Data>>() {
          @Override public boolean test(Response<ReviewsByEpisodeQuery.Data> response) throws Exception {
            assertThat(response.data().reviews()).hasSize(3);
            assertThat(response.data().reviews().get(2).stars()).isEqualTo(5);
            assertThat(response.data().reviews().get(2).commentary()).isEqualTo("Amazing");
            return true;
          }
        }
    );

    CreateReviewMutation mutation = new CreateReviewMutation(
        Episode.EMPIRE,
        ReviewInput.builder().stars(5).commentary("Awesome").favoriteColor(ColorInput.builder().build()).build()
    );

    server.enqueue(Utils.INSTANCE.mockResponse("CreateReviewResponse.json"));
    server.enqueue(Utils.INSTANCE.mockResponse("ReviewsEmpireEpisodeResponseUpdated.json"));

    RealApolloCall call = (RealApolloCall) apolloClient.mutate(mutation).refetchQueries(new ReviewsByEpisodeQuery(Episode.EMPIRE));
    Rx2Apollo
        .from(call)
        .test();
    assertThat(server.getRequestCount()).isEqualTo(3);

    Utils.INSTANCE.assertResponse(
        apolloClient.query(new ReviewsByEpisodeQuery(Episode.EMPIRE)).responseFetcher(CACHE_ONLY),
        new Predicate<Response<ReviewsByEpisodeQuery.Data>>() {
          @Override public boolean test(Response<ReviewsByEpisodeQuery.Data> response) throws Exception {
            assertThat(response.data().reviews()).hasSize(4);
            assertThat(response.data().reviews().get(3).stars()).isEqualTo(5);
            assertThat(response.data().reviews().get(3).commentary()).isEqualTo("Awesome");
            return true;
          }
        }
    );
  }

  @Test @SuppressWarnings("CheckReturnValue") public void refetchWatchers() throws Exception {
    server.enqueue(Utils.INSTANCE.mockResponse("ReviewsEmpireEpisodeResponse.json"));
    server.enqueue(Utils.INSTANCE.mockResponse("CreateReviewResponse.json"));
    server.enqueue(Utils.INSTANCE.mockResponse("ReviewsEmpireEpisodeResponseUpdated.json"));

    final AtomicReference<Response<ReviewsByEpisodeQuery.Data>> empireReviewsWatchResponse = new AtomicReference<>();
    ApolloQueryWatcher<ReviewsByEpisodeQuery.Data> queryWatcher = apolloClient.query(new ReviewsByEpisodeQuery(Episode.EMPIRE))
        .watcher()
        .refetchResponseFetcher(NETWORK_FIRST)
        .enqueueAndWatch(new ApolloCall.Callback<ReviewsByEpisodeQuery.Data>() {
          @Override public void onResponse(@NotNull Response<ReviewsByEpisodeQuery.Data> response) {
            empireReviewsWatchResponse.set(response);
          }

          @Override public void onFailure(@NotNull ApolloException e) {
          }
        });

    CreateReviewMutation mutation = new CreateReviewMutation(
        Episode.EMPIRE,
        ReviewInput.builder().stars(5).commentary("Awesome").favoriteColor(ColorInput.builder().build()).build()
    );
    Rx2Apollo
        .from(apolloClient.mutate(mutation).refetchQueries(queryWatcher.operation().name()))
        .test();
    assertThat(server.getRequestCount()).isEqualTo(3);

    Response<ReviewsByEpisodeQuery.Data> empireReviewsQueryResponse = empireReviewsWatchResponse.get();
    assertThat(empireReviewsQueryResponse.data().reviews()).hasSize(4);
    assertThat(empireReviewsQueryResponse.data().reviews().get(3).stars()).isEqualTo(5);
    assertThat(empireReviewsQueryResponse.data().reviews().get(3).commentary()).isEqualTo("Awesome");

    queryWatcher.cancel();
  }
}
