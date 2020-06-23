package com.apollographql.apollo;

import com.apollographql.apollo.api.CustomTypeAdapter;
import com.apollographql.apollo.api.CustomTypeValue;
import com.apollographql.apollo.api.Input;
import com.apollographql.apollo.api.Query;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.cache.normalized.CacheKey;
import com.apollographql.apollo.cache.normalized.lru.EvictionPolicy;
import com.apollographql.apollo.cache.normalized.lru.LruNormalizedCacheFactory;
import com.apollographql.apollo.integration.normalizer.EpisodeHeroWithDatesQuery;
import com.apollographql.apollo.integration.normalizer.EpisodeHeroWithInlineFragmentQuery;
import com.apollographql.apollo.integration.normalizer.HeroAndFriendsNamesWithIDsQuery;
import com.apollographql.apollo.integration.normalizer.HeroAndFriendsWithFragmentsQuery;
import com.apollographql.apollo.integration.normalizer.HeroNameWithEnumsQuery;
import com.apollographql.apollo.integration.normalizer.StarshipByIdQuery;
import com.apollographql.apollo.integration.normalizer.fragment.HeroWithFriendsFragment;
import com.apollographql.apollo.integration.normalizer.fragment.HumanWithIdFragment;
import com.apollographql.apollo.integration.normalizer.type.CustomType;
import com.apollographql.apollo.integration.normalizer.type.Episode;
import io.reactivex.functions.Predicate;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

import static com.apollographql.apollo.fetcher.ApolloResponseFetchers.CACHE_ONLY;
import static com.apollographql.apollo.integration.normalizer.type.Episode.JEDI;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

@SuppressWarnings("SimpleDateFormatConstant")
public class ResponseWriteTestCase {
  private ApolloClient apolloClient;
  @Rule public final MockWebServer server = new MockWebServer();
  private SimpleDateFormat DATE_TIME_FORMAT = new SimpleDateFormat("yyyy-mm-dd", Locale.US);

  @Before public void setUp() {
    OkHttpClient okHttpClient = new OkHttpClient.Builder()
        .dispatcher(new Dispatcher(Utils.INSTANCE.immediateExecutorService()))
        .build();

    apolloClient = ApolloClient.builder()
        .serverUrl(server.url("/"))
        .okHttpClient(okHttpClient)
        .normalizedCache(new LruNormalizedCacheFactory(EvictionPolicy.NO_EVICTION), new IdFieldCacheKeyResolver())
        .dispatcher(Utils.INSTANCE.immediateExecutor())
        .addCustomTypeAdapter(CustomType.DATE, new CustomTypeAdapter<Date>() {
          @Override public Date decode(CustomTypeValue value) {
            try {
              return DATE_TIME_FORMAT.parse(value.value.toString());
            } catch (ParseException e) {
              throw new RuntimeException(e);
            }
          }

          @Override public CustomTypeValue encode(Date value) {
            return new CustomTypeValue.GraphQLString(DATE_TIME_FORMAT.format(value));
          }
        })
        .build();
  }

  @Test
  public void customType() throws Exception {
    EpisodeHeroWithDatesQuery query = new EpisodeHeroWithDatesQuery(Input.fromNullable(JEDI));

    Utils.INSTANCE.enqueueAndAssertResponse(
        server,
        "EpisodeHeroWithDatesResponse.json",
        apolloClient.query(query),
        new Predicate<Response<EpisodeHeroWithDatesQuery.Data>>() {
          @Override public boolean test(Response<EpisodeHeroWithDatesQuery.Data> response) throws Exception {
            assertThat(response.data().hero().__typename()).isEqualTo("Droid");
            assertThat(response.data().hero().heroName()).isEqualTo("R2-D2");
            assertThat(DATE_TIME_FORMAT.format(response.data().hero().birthDate())).isEqualTo("1984-04-16");
            assertThat(response.data().hero().showUpDates()).hasSize(3);
            assertThat(DATE_TIME_FORMAT.format(response.data().hero().showUpDates().get(0))).isEqualTo("2017-01-16");
            assertThat(DATE_TIME_FORMAT.format(response.data().hero().showUpDates().get(1))).isEqualTo("2017-02-16");
            assertThat(DATE_TIME_FORMAT.format(response.data().hero().showUpDates().get(2))).isEqualTo("2017-03-16");
            return true;
          }
        }
    );

    EpisodeHeroWithDatesQuery.Hero hero = new EpisodeHeroWithDatesQuery.Hero(
        "Droid",
        "R222-D222",
        DATE_TIME_FORMAT.parse("1985-04-16"),
        Collections.<Date>emptyList()
    );
    apolloClient.getApolloStore().write(query, new EpisodeHeroWithDatesQuery.Data(hero)).execute();

    assertCachedQueryResponse(
        query,
        new Predicate<Response<EpisodeHeroWithDatesQuery.Data>>() {
          @Override public boolean test(Response<EpisodeHeroWithDatesQuery.Data> response) throws Exception {
            assertThat(response.data().hero().__typename()).isEqualTo("Droid");
            assertThat(response.data().hero().heroName()).isEqualTo("R222-D222");
            assertThat(DATE_TIME_FORMAT.format(response.data().hero().birthDate())).isEqualTo("1985-04-16");
            assertThat(response.data().hero().showUpDates()).hasSize(0);
            return true;
          }
        }
    );

    hero = new EpisodeHeroWithDatesQuery.Hero(
        hero.__typename(),
        "R22-D22",
        DATE_TIME_FORMAT.parse("1986-04-16"),
        asList(
            DATE_TIME_FORMAT.parse("2017-04-16"),
            DATE_TIME_FORMAT.parse("2017-05-16")
        )
    );
    apolloClient.getApolloStore().write(query, new EpisodeHeroWithDatesQuery.Data(hero)).execute();

    assertCachedQueryResponse(
        query,
        new Predicate<Response<EpisodeHeroWithDatesQuery.Data>>() {
          @Override public boolean test(Response<EpisodeHeroWithDatesQuery.Data> response) throws Exception {
            assertThat(response.data().hero().__typename()).isEqualTo("Droid");
            assertThat(response.data().hero().heroName()).isEqualTo("R22-D22");
            assertThat(DATE_TIME_FORMAT.format(response.data().hero().birthDate())).isEqualTo("1986-04-16");
            assertThat(response.data().hero().showUpDates()).hasSize(2);
            assertThat(DATE_TIME_FORMAT.format(response.data().hero().showUpDates().get(0))).isEqualTo("2017-04-16");
            assertThat(DATE_TIME_FORMAT.format(response.data().hero().showUpDates().get(1))).isEqualTo("2017-05-16");
            return true;
          }
        }
    );
  }

  @Test
  public void enums() throws Exception {
    HeroNameWithEnumsQuery query = new HeroNameWithEnumsQuery();

    Utils.INSTANCE.enqueueAndAssertResponse(
        server,
        "HeroNameWithEnumsResponse.json",
        apolloClient.query(query),
        new Predicate<Response<HeroNameWithEnumsQuery.Data>>() {
          @Override public boolean test(Response<HeroNameWithEnumsQuery.Data> response) throws Exception {
            assertThat(response.data().hero().__typename()).isEqualTo("Droid");
            assertThat(response.data().hero().name()).isEqualTo("R2-D2");
            assertThat(response.data().hero().firstAppearsIn()).isEqualTo(Episode.EMPIRE);
            assertThat(response.data().hero().appearsIn()).hasSize(3);
            assertThat(response.data().hero().appearsIn().get(0)).isEqualTo(Episode.NEWHOPE);
            assertThat(response.data().hero().appearsIn().get(1)).isEqualTo(Episode.EMPIRE);
            assertThat(response.data().hero().appearsIn().get(2)).isEqualTo(Episode.JEDI);
            return true;
          }
        }
    );

    HeroNameWithEnumsQuery.Hero hero = new HeroNameWithEnumsQuery.Hero(
        "Droid",
        "R222-D222",
        Episode.JEDI,
        Collections.<Episode>emptyList()
    );
    apolloClient.getApolloStore().write(query, new HeroNameWithEnumsQuery.Data(hero)).execute();

    assertCachedQueryResponse(
        query,
        new Predicate<Response<HeroNameWithEnumsQuery.Data>>() {
          @Override public boolean test(Response<HeroNameWithEnumsQuery.Data> response) throws Exception {
            assertThat(response.data().hero().__typename()).isEqualTo("Droid");
            assertThat(response.data().hero().name()).isEqualTo("R222-D222");
            assertThat(response.data().hero().firstAppearsIn()).isEqualTo(Episode.JEDI);
            assertThat(response.data().hero().appearsIn()).hasSize(0);
            return true;
          }
        }
    );

    hero = new HeroNameWithEnumsQuery.Hero(
        hero.__typename(),
        "R22-D22",
        Episode.JEDI,
        asList(Episode.EMPIRE)
    );
    apolloClient.getApolloStore().write(query, new HeroNameWithEnumsQuery.Data(hero)).execute();

    assertCachedQueryResponse(
        query,
        new Predicate<Response<HeroNameWithEnumsQuery.Data>>() {
          @Override public boolean test(Response<HeroNameWithEnumsQuery.Data> response) throws Exception {
            assertThat(response.data().hero().__typename()).isEqualTo("Droid");
            assertThat(response.data().hero().name()).isEqualTo("R22-D22");
            assertThat(response.data().hero().firstAppearsIn()).isEqualTo(Episode.JEDI);
            assertThat(response.data().hero().appearsIn()).hasSize(1);
            assertThat(response.data().hero().appearsIn().get(0)).isEqualTo(Episode.EMPIRE);
            return true;
          }
        }
    );
  }

  @Test
  public void objects() throws Exception {
    HeroAndFriendsNamesWithIDsQuery query = new HeroAndFriendsNamesWithIDsQuery(Input.fromNullable(JEDI));

    Utils.INSTANCE.enqueueAndAssertResponse(
        server,
        "HeroAndFriendsNameWithIdsResponse.json",
        apolloClient.query(query),
        new Predicate<Response<HeroAndFriendsNamesWithIDsQuery.Data>>() {
          @Override public boolean test(Response<HeroAndFriendsNamesWithIDsQuery.Data> response) throws Exception {
            assertThat(response.data().hero().__typename()).isEqualTo("Droid");
            assertThat(response.data().hero().name()).isEqualTo("R2-D2");
            assertThat(response.data().hero().id()).isEqualTo("2001");
            assertThat(response.data().hero().friends()).hasSize(3);
            assertThat(response.data().hero().friends().get(0).__typename()).isEqualTo("Human");
            assertThat(response.data().hero().friends().get(0).id()).isEqualTo("1000");
            assertThat(response.data().hero().friends().get(0).name()).isEqualTo("Luke Skywalker");
            assertThat(response.data().hero().friends().get(1).__typename()).isEqualTo("Human");
            assertThat(response.data().hero().friends().get(1).id()).isEqualTo("1002");
            assertThat(response.data().hero().friends().get(1).name()).isEqualTo("Han Solo");
            assertThat(response.data().hero().friends().get(2).__typename()).isEqualTo("Human");
            assertThat(response.data().hero().friends().get(2).id()).isEqualTo("1003");
            assertThat(response.data().hero().friends().get(2).name()).isEqualTo("Leia Organa");
            return true;
          }
        }
    );

    HeroAndFriendsNamesWithIDsQuery.Hero hero = new HeroAndFriendsNamesWithIDsQuery.Hero(
        "Droid",
        "2001",
        "R222-D222",
        null
    );
    apolloClient.getApolloStore().write(query, new HeroAndFriendsNamesWithIDsQuery.Data(hero)).execute();

    assertCachedQueryResponse(
        query,
        new Predicate<Response<HeroAndFriendsNamesWithIDsQuery.Data>>() {
          @Override public boolean test(Response<HeroAndFriendsNamesWithIDsQuery.Data> response) throws Exception {
            assertThat(response.data().hero().__typename()).isEqualTo("Droid");
            assertThat(response.data().hero().name()).isEqualTo("R222-D222");
            assertThat(response.data().hero().id()).isEqualTo("2001");
            assertThat(response.data().hero().friends()).isNull();
            return true;
          }
        }
    );

    HeroAndFriendsNamesWithIDsQuery.Friend friend = new HeroAndFriendsNamesWithIDsQuery.Friend(
        "Human",
        "1002",
        "Han Soloooo"
    );
    hero = new HeroAndFriendsNamesWithIDsQuery.Hero(
        hero.__typename(),
        hero.id(),
        "R222-D222",
        singletonList(friend)
    );
    apolloClient.getApolloStore().write(query, new HeroAndFriendsNamesWithIDsQuery.Data(hero)).execute();

    assertCachedQueryResponse(
        query,
        new Predicate<Response<HeroAndFriendsNamesWithIDsQuery.Data>>() {
          @Override public boolean test(Response<HeroAndFriendsNamesWithIDsQuery.Data> response) throws Exception {
            assertThat(response.data().hero().__typename()).isEqualTo("Droid");
            assertThat(response.data().hero().name()).isEqualTo("R222-D222");
            assertThat(response.data().hero().id()).isEqualTo("2001");
            assertThat(response.data().hero().friends()).hasSize(1);
            assertThat(response.data().hero().friends().get(0).__typename()).isEqualTo("Human");
            assertThat(response.data().hero().friends().get(0).id()).isEqualTo("1002");
            assertThat(response.data().hero().friends().get(0).name()).isEqualTo("Han Soloooo");
            return true;
          }
        }
    );
  }

  @Test
  public void operation_with_fragments() throws Exception {
    HeroAndFriendsWithFragmentsQuery query = new HeroAndFriendsWithFragmentsQuery(Input.fromNullable(Episode.NEWHOPE));

    Utils.INSTANCE.enqueueAndAssertResponse(
        server,
        "HeroAndFriendsWithFragmentResponse.json",
        apolloClient.query(query),
        new Predicate<Response<HeroAndFriendsWithFragmentsQuery.Data>>() {
          @Override public boolean test(Response<HeroAndFriendsWithFragmentsQuery.Data> response) throws Exception {
            assertThat(response.data().hero().__typename()).isEqualTo("Droid");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().__typename()).isEqualTo("Droid");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().id()).isEqualTo("2001");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().name()).isEqualTo("R2-D2");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends()).hasSize(3);
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends().get(0).fragments().humanWithIdFragment().__typename()).isEqualTo("Human");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends().get(0).fragments().humanWithIdFragment().id()).isEqualTo("1000");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends().get(0).fragments().humanWithIdFragment().name()).isEqualTo("Luke Skywalker");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends().get(1).fragments().humanWithIdFragment().__typename()).isEqualTo("Human");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends().get(1).fragments().humanWithIdFragment().id()).isEqualTo("1002");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends().get(1).fragments().humanWithIdFragment().name()).isEqualTo("Han Solo");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends().get(2).fragments().humanWithIdFragment().__typename()).isEqualTo("Human");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends().get(2).fragments().humanWithIdFragment().id()).isEqualTo("1003");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends().get(2).fragments().humanWithIdFragment().name()).isEqualTo("Leia Organa");
            return true;
          }
        }
    );

    HeroAndFriendsWithFragmentsQuery.Hero hero = new HeroAndFriendsWithFragmentsQuery.Hero(
        "Droid",
        new HeroAndFriendsWithFragmentsQuery.Hero.Fragments(
            new HeroWithFriendsFragment(
                "Droid",
                "2001",
                "R222-D222",
                asList(
                    new HeroWithFriendsFragment.Friend(
                        "Human",
                        new HeroWithFriendsFragment.Friend.Fragments(
                            new HumanWithIdFragment(
                                "Human",
                                "1006",
                                "SuperMan"
                            )
                        )
                    ),
                    new HeroWithFriendsFragment.Friend(
                        "Human",
                        new HeroWithFriendsFragment.Friend.Fragments(
                            new HumanWithIdFragment(
                                "Human",
                                "1004",
                                "Beast"
                            )
                        )
                    )
                )
            )
        )
    );
    apolloClient.getApolloStore().write(query, new HeroAndFriendsWithFragmentsQuery.Data(hero)).execute();

    assertCachedQueryResponse(
        query,
        new Predicate<Response<HeroAndFriendsWithFragmentsQuery.Data>>() {
          @Override public boolean test(Response<HeroAndFriendsWithFragmentsQuery.Data> response) throws Exception {
            assertThat(response.data().hero().__typename()).isEqualTo("Droid");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().__typename()).isEqualTo("Droid");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().id()).isEqualTo("2001");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().name()).isEqualTo("R222-D222");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends()).hasSize(2);
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends().get(0).fragments().humanWithIdFragment().__typename()).isEqualTo("Human");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends().get(0).fragments().humanWithIdFragment().id()).isEqualTo("1006");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends().get(0).fragments().humanWithIdFragment().name()).isEqualTo("SuperMan");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends().get(1).fragments().humanWithIdFragment().__typename()).isEqualTo("Human");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends().get(1).fragments().humanWithIdFragment().id()).isEqualTo("1004");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends().get(1).fragments().humanWithIdFragment().name()).isEqualTo("Beast");
            return true;
          }
        }
    );
  }

  @SuppressWarnings("CheckReturnValue")
  @Test
  public void operation_with_inline_fragments() throws Exception {
    EpisodeHeroWithInlineFragmentQuery query = new EpisodeHeroWithInlineFragmentQuery(Input.fromNullable(Episode.NEWHOPE));

    Utils.INSTANCE.enqueueAndAssertResponse(
        server,
        "EpisodeHeroWithInlineFragmentResponse.json",
        apolloClient.query(query),
        new Predicate<Response<EpisodeHeroWithInlineFragmentQuery.Data>>() {
          @Override
          public boolean test(Response<EpisodeHeroWithInlineFragmentQuery.Data> response) throws Exception {
            assertThat(response.data().hero().__typename()).isEqualTo("Droid");
            assertThat(response.data().hero().name()).isEqualTo("R2-D2");
            assertThat(response.data().hero().friends()).hasSize(3);

            EpisodeHeroWithInlineFragmentQuery.AsHuman asHuman = (EpisodeHeroWithInlineFragmentQuery.AsHuman) response.data().hero().friends().get(0);
            assertThat(asHuman.__typename()).isEqualTo("Human");
            assertThat(asHuman.id()).isEqualTo("1000");
            assertThat(asHuman.name()).isEqualTo("Luke Skywalker");
            assertThat(asHuman.height()).isWithin(1.5);

            EpisodeHeroWithInlineFragmentQuery.AsDroid asDroid1 = (EpisodeHeroWithInlineFragmentQuery.AsDroid) response.data().hero().friends().get(1);
            assertThat(asDroid1.__typename()).isEqualTo("Droid");
            assertThat(asDroid1.name()).isEqualTo("Android");
            assertThat(asDroid1.primaryFunction()).isEqualTo("Hunt and destroy iOS devices");

            EpisodeHeroWithInlineFragmentQuery.AsDroid asDroid2 = (EpisodeHeroWithInlineFragmentQuery.AsDroid) response.data().hero().friends().get(2);
            assertThat(asDroid2.__typename()).isEqualTo("Droid");
            assertThat(asDroid2.name()).isEqualTo("Battle Droid");
            assertThat(asDroid2.primaryFunction()).isEqualTo("Controlled alternative to human soldiers");
            return true;
          }
        }
    );

    EpisodeHeroWithInlineFragmentQuery.Hero hero = new EpisodeHeroWithInlineFragmentQuery.Hero(
        "Droid",
        "R22-D22",
        asList(
            new EpisodeHeroWithInlineFragmentQuery.AsHuman(
                "Human",
                "1002",
                "Han Solo",
                2.5
            ),
            new EpisodeHeroWithInlineFragmentQuery.AsDroid(
                "Droid",
                "RD",
                "Entertainment"
            )
        )
    );
    apolloClient.getApolloStore().write(query, new EpisodeHeroWithInlineFragmentQuery.Data(hero)).execute();

    assertCachedQueryResponse(
        query,
        new Predicate<Response<EpisodeHeroWithInlineFragmentQuery.Data>>() {
          @Override public boolean test(Response<EpisodeHeroWithInlineFragmentQuery.Data> response) throws Exception {
            assertThat(response.data().hero().__typename()).isEqualTo("Droid");
            assertThat(response.data().hero().name()).isEqualTo("R22-D22");
            assertThat(response.data().hero().friends()).hasSize(2);

            EpisodeHeroWithInlineFragmentQuery.AsHuman asHuman = (EpisodeHeroWithInlineFragmentQuery.AsHuman) response.data().hero().friends().get(0);
            assertThat(asHuman.__typename()).isEqualTo("Human");
            assertThat(asHuman.id()).isEqualTo("1002");
            assertThat(asHuman.name()).isEqualTo("Han Solo");
            assertThat(asHuman.height()).isWithin(2.5);

            EpisodeHeroWithInlineFragmentQuery.AsDroid asDroid = (EpisodeHeroWithInlineFragmentQuery.AsDroid) response.data().hero().friends().get(1);
            assertThat(asDroid.__typename()).isEqualTo("Droid");
            assertThat(asDroid.name()).isEqualTo("RD");
            assertThat(asDroid.primaryFunction()).isEqualTo("Entertainment");
            return true;
          }
        }
    );
  }

  @Test
  public void fragments() throws Exception {
    HeroAndFriendsWithFragmentsQuery query = new HeroAndFriendsWithFragmentsQuery(Input.fromNullable(Episode.NEWHOPE));

    Utils.INSTANCE.enqueueAndAssertResponse(
        server,
        "HeroAndFriendsWithFragmentResponse.json",
        apolloClient.query(query),
        new Predicate<Response<HeroAndFriendsWithFragmentsQuery.Data>>() {
          @Override public boolean test(Response<HeroAndFriendsWithFragmentsQuery.Data> response) throws Exception {
            assertThat(response.data().hero().__typename()).isEqualTo("Droid");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().__typename()).isEqualTo("Droid");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().id()).isEqualTo("2001");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().name()).isEqualTo("R2-D2");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends()).hasSize(3);
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends().get(0).fragments().humanWithIdFragment().__typename()).isEqualTo("Human");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends().get(0).fragments().humanWithIdFragment().id()).isEqualTo("1000");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends().get(0).fragments().humanWithIdFragment().name()).isEqualTo("Luke Skywalker");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends().get(1).fragments().humanWithIdFragment().__typename()).isEqualTo("Human");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends().get(1).fragments().humanWithIdFragment().id()).isEqualTo("1002");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends().get(1).fragments().humanWithIdFragment().name()).isEqualTo("Han Solo");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends().get(2).fragments().humanWithIdFragment().__typename()).isEqualTo("Human");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends().get(2).fragments().humanWithIdFragment().id()).isEqualTo("1003");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends().get(2).fragments().humanWithIdFragment().name()).isEqualTo("Leia Organa");
            return true;
          }
        }
    );

    apolloClient.getApolloStore().write(
        new HeroWithFriendsFragment(
            "Droid",
            "2001",
            "R222-D222",
            asList(
                new HeroWithFriendsFragment.Friend(
                    "Human",
                    new HeroWithFriendsFragment.Friend.Fragments(
                        new HumanWithIdFragment(
                            "Human",
                            "1000",
                            "SuperMan"
                        )
                    )
                ),
                new HeroWithFriendsFragment.Friend(
                    "Human",
                    new HeroWithFriendsFragment.Friend.Fragments(
                        new HumanWithIdFragment(
                            "Human",
                            "1002",
                            "Han Solo"
                        )
                    )
                )
            )
        ), CacheKey.from("2001"), query.variables()
    ).execute();

    apolloClient.getApolloStore().write(
        new HumanWithIdFragment(
            "Human",
            "1002",
            "Beast"
        ), CacheKey.from("1002"), query.variables()
    ).execute();

    assertCachedQueryResponse(
        query,
        new Predicate<Response<HeroAndFriendsWithFragmentsQuery.Data>>() {
          @Override public boolean test(Response<HeroAndFriendsWithFragmentsQuery.Data> response) throws Exception {
            assertThat(response.data().hero().__typename()).isEqualTo("Droid");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().__typename()).isEqualTo("Droid");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().id()).isEqualTo("2001");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().name()).isEqualTo("R222-D222");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends()).hasSize(2);
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends().get(0).fragments().humanWithIdFragment().__typename()).isEqualTo("Human");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends().get(0).fragments().humanWithIdFragment().id()).isEqualTo("1000");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends().get(0).fragments().humanWithIdFragment().name()).isEqualTo("SuperMan");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends().get(1).fragments().humanWithIdFragment().__typename()).isEqualTo("Human");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends().get(1).fragments().humanWithIdFragment().id()).isEqualTo("1002");
            assertThat(response.data().hero().fragments().heroWithFriendsFragment().friends().get(1).fragments().humanWithIdFragment().name()).isEqualTo("Beast");
            return true;
          }
        }
    );
  }

  @Test public void listOfList() throws Exception {
    StarshipByIdQuery query = new StarshipByIdQuery("Starship1");

    Utils.INSTANCE.enqueueAndAssertResponse(
        server,
        "StarshipByIdResponse.json",
        apolloClient.query(query),
        new Predicate<Response<StarshipByIdQuery.Data>>() {
          @Override public boolean test(Response<StarshipByIdQuery.Data> response) throws Exception {
            assertThat(response.data().starship().__typename()).isEqualTo("Starship");
            assertThat(response.data().starship().name()).isEqualTo("SuperRocket");
            assertThat(response.data().starship().coordinates()).hasSize(3);
            assertThat(response.data().starship().coordinates()).containsExactly(asList(100d, 200d), asList(300d, 400d),
                asList(500d, 600d));
            return true;
          }
        }
    );

    StarshipByIdQuery.Starship starship = new StarshipByIdQuery.Starship(
        "Starship",
        "Starship1",
        "SuperRocket",
        asList(asList(900d, 800d), asList(700d, 600d))
    );
    apolloClient.getApolloStore().write(query, new StarshipByIdQuery.Data(starship)).execute();

    assertCachedQueryResponse(
        query,
        new Predicate<Response<StarshipByIdQuery.Data>>() {
          @Override public boolean test(Response<StarshipByIdQuery.Data> response) throws Exception {
            assertThat(response.data().starship().__typename()).isEqualTo("Starship");
            assertThat(response.data().starship().name()).isEqualTo("SuperRocket");
            assertThat(response.data().starship().coordinates()).hasSize(2);
            assertThat(response.data().starship().coordinates()).containsExactly(asList(900d, 800d), asList(700d, 600d));
            return true;
          }
        }
    );
  }

  private <T> void assertCachedQueryResponse(Query<?, T, ?> query, Predicate<Response<T>> predicate) throws Exception {
    Utils.INSTANCE.assertResponse(
        apolloClient.query(query).responseFetcher(CACHE_ONLY),
        predicate
    );
  }
}
