// AUTO-GENERATED FILE. DO NOT MODIFY.
//
// This class was automatically generated by Apollo GraphQL plugin from the GraphQL queries it found.
// It should not be modified by hand.
//
package com.example.directive_with_fragment;

import com.apollographql.apollo.api.Operation;
import com.apollographql.apollo.api.OperationName;
import com.apollographql.apollo.api.Query;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.api.ResponseField;
import com.apollographql.apollo.api.ScalarTypeAdapters;
import com.apollographql.apollo.api.internal.InputFieldMarshaller;
import com.apollographql.apollo.api.internal.InputFieldWriter;
import com.apollographql.apollo.api.internal.OperationRequestBodyComposer;
import com.apollographql.apollo.api.internal.Optional;
import com.apollographql.apollo.api.internal.QueryDocumentMinifier;
import com.apollographql.apollo.api.internal.ResponseFieldMapper;
import com.apollographql.apollo.api.internal.ResponseFieldMarshaller;
import com.apollographql.apollo.api.internal.ResponseReader;
import com.apollographql.apollo.api.internal.ResponseWriter;
import com.apollographql.apollo.api.internal.SimpleOperationResponseParser;
import com.apollographql.apollo.api.internal.Utils;
import com.example.directive_with_fragment.fragment.HeroDetails;
import com.example.directive_with_fragment.fragment.HumanDetails;
import com.example.directive_with_fragment.type.CustomType;
import java.io.IOException;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import okio.Buffer;
import okio.BufferedSource;
import okio.ByteString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TestQuery implements Query<TestQuery.Data, Optional<TestQuery.Data>, TestQuery.Variables> {
  public static final String OPERATION_ID = "e7ae0709b15d61fbba95a5c2e74b439fbed8ccf8d68fd389f4dd8250b55efeaf";

  public static final String QUERY_DOCUMENT = QueryDocumentMinifier.minify(
    "query TestQuery($withDetails: Boolean!, $skipHumanDetails: Boolean!) {\n"
        + "  hero {\n"
        + "    __typename\n"
        + "    id\n"
        + "    ... HeroDetails @include(if: $withDetails) @skip(if: $skipHumanDetails)\n"
        + "    ... HumanDetails @include(if: $withDetails)\n"
        + "  }\n"
        + "}\n"
        + "fragment HeroDetails on Character {\n"
        + "  __typename\n"
        + "  name\n"
        + "}\n"
        + "fragment HumanDetails on Human {\n"
        + "  __typename\n"
        + "  homePlanet\n"
        + "}"
  );

  public static final OperationName OPERATION_NAME = new OperationName() {
    @Override
    public String name() {
      return "TestQuery";
    }
  };

  private final TestQuery.Variables variables;

  public TestQuery(boolean withDetails, boolean skipHumanDetails) {
    variables = new TestQuery.Variables(withDetails, skipHumanDetails);
  }

  @Override
  public String operationId() {
    return OPERATION_ID;
  }

  @Override
  public String queryDocument() {
    return QUERY_DOCUMENT;
  }

  @Override
  public Optional<TestQuery.Data> wrapData(TestQuery.Data data) {
    return Optional.fromNullable(data);
  }

  @Override
  public TestQuery.Variables variables() {
    return variables;
  }

  @Override
  public ResponseFieldMapper<TestQuery.Data> responseFieldMapper() {
    return new Data.Mapper();
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public OperationName name() {
    return OPERATION_NAME;
  }

  @Override
  @NotNull
  public Response<Optional<TestQuery.Data>> parse(@NotNull final BufferedSource source,
      @NotNull final ScalarTypeAdapters scalarTypeAdapters) throws IOException {
    return SimpleOperationResponseParser.parse(source, this, scalarTypeAdapters);
  }

  @Override
  @NotNull
  public Response<Optional<TestQuery.Data>> parse(@NotNull final ByteString byteString,
      @NotNull final ScalarTypeAdapters scalarTypeAdapters) throws IOException {
    return parse(new Buffer().write(byteString), scalarTypeAdapters);
  }

  @Override
  @NotNull
  public Response<Optional<TestQuery.Data>> parse(@NotNull final BufferedSource source) throws
      IOException {
    return parse(source, ScalarTypeAdapters.DEFAULT);
  }

  @Override
  @NotNull
  public Response<Optional<TestQuery.Data>> parse(@NotNull final ByteString byteString) throws
      IOException {
    return parse(byteString, ScalarTypeAdapters.DEFAULT);
  }

  @Override
  @NotNull
  public ByteString composeRequestBody(@NotNull final ScalarTypeAdapters scalarTypeAdapters) {
    return OperationRequestBodyComposer.compose(this, false, true, scalarTypeAdapters);
  }

  @NotNull
  @Override
  public ByteString composeRequestBody() {
    return OperationRequestBodyComposer.compose(this, false, true, ScalarTypeAdapters.DEFAULT);
  }

  @Override
  @NotNull
  public ByteString composeRequestBody(final boolean autoPersistQueries,
      final boolean withQueryDocument, @NotNull final ScalarTypeAdapters scalarTypeAdapters) {
    return OperationRequestBodyComposer.compose(this, autoPersistQueries, withQueryDocument, scalarTypeAdapters);
  }

  public static final class Builder {
    private boolean withDetails;

    private boolean skipHumanDetails;

    Builder() {
    }

    public Builder withDetails(boolean withDetails) {
      this.withDetails = withDetails;
      return this;
    }

    public Builder skipHumanDetails(boolean skipHumanDetails) {
      this.skipHumanDetails = skipHumanDetails;
      return this;
    }

    public TestQuery build() {
      return new TestQuery(withDetails, skipHumanDetails);
    }
  }

  public static final class Variables extends Operation.Variables {
    private final boolean withDetails;

    private final boolean skipHumanDetails;

    private final transient Map<String, Object> valueMap = new LinkedHashMap<>();

    Variables(boolean withDetails, boolean skipHumanDetails) {
      this.withDetails = withDetails;
      this.skipHumanDetails = skipHumanDetails;
      this.valueMap.put("withDetails", withDetails);
      this.valueMap.put("skipHumanDetails", skipHumanDetails);
    }

    public boolean withDetails() {
      return withDetails;
    }

    public boolean skipHumanDetails() {
      return skipHumanDetails;
    }

    @Override
    public Map<String, Object> valueMap() {
      return Collections.unmodifiableMap(valueMap);
    }

    @Override
    public InputFieldMarshaller marshaller() {
      return new InputFieldMarshaller() {
        @Override
        public void marshal(InputFieldWriter writer) throws IOException {
          writer.writeBoolean("withDetails", withDetails);
          writer.writeBoolean("skipHumanDetails", skipHumanDetails);
        }
      };
    }
  }

  /**
   * Data from the response after executing this GraphQL operation
   */
  public static class Data implements Operation.Data {
    static final ResponseField[] $responseFields = {
      ResponseField.forObject("hero", "hero", null, true, Collections.<ResponseField.Condition>emptyList())
    };

    final Optional<Hero> hero;

    private transient volatile String $toString;

    private transient volatile int $hashCode;

    private transient volatile boolean $hashCodeMemoized;

    public Data(@Nullable Hero hero) {
      this.hero = Optional.fromNullable(hero);
    }

    public Optional<Hero> hero() {
      return this.hero;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public ResponseFieldMarshaller marshaller() {
      return new ResponseFieldMarshaller() {
        @Override
        public void marshal(ResponseWriter writer) {
          writer.writeObject($responseFields[0], hero.isPresent() ? hero.get().marshaller() : null);
        }
      };
    }

    @Override
    public String toString() {
      if ($toString == null) {
        $toString = "Data{"
          + "hero=" + hero
          + "}";
      }
      return $toString;
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) {
        return true;
      }
      if (o instanceof Data) {
        Data that = (Data) o;
        return this.hero.equals(that.hero);
      }
      return false;
    }

    @Override
    public int hashCode() {
      if (!$hashCodeMemoized) {
        int h = 1;
        h *= 1000003;
        h ^= hero.hashCode();
        $hashCode = h;
        $hashCodeMemoized = true;
      }
      return $hashCode;
    }

    public static final class Mapper implements ResponseFieldMapper<Data> {
      final Hero.Mapper heroFieldMapper = new Hero.Mapper();

      @Override
      public Data map(ResponseReader reader) {
        final Hero hero = reader.readObject($responseFields[0], new ResponseReader.ObjectReader<Hero>() {
          @Override
          public Hero read(ResponseReader reader) {
            return heroFieldMapper.map(reader);
          }
        });
        return new Data(hero);
      }
    }
  }

  /**
   * A character from the Star Wars universe
   */
  public static class Hero {
    static final ResponseField[] $responseFields = {
      ResponseField.forString("__typename", "__typename", null, false, Collections.<ResponseField.Condition>emptyList()),
      ResponseField.forCustomType("id", "id", null, false, CustomType.ID, Collections.<ResponseField.Condition>emptyList()),
      ResponseField.forString("__typename", "__typename", null, false, Collections.<ResponseField.Condition>emptyList())
    };

    final @NotNull String __typename;

    final @NotNull String id;

    private final @NotNull Fragments fragments;

    private transient volatile String $toString;

    private transient volatile int $hashCode;

    private transient volatile boolean $hashCodeMemoized;

    public Hero(@NotNull String __typename, @NotNull String id, @NotNull Fragments fragments) {
      this.__typename = Utils.checkNotNull(__typename, "__typename == null");
      this.id = Utils.checkNotNull(id, "id == null");
      this.fragments = Utils.checkNotNull(fragments, "fragments == null");
    }

    public @NotNull String __typename() {
      return this.__typename;
    }

    /**
     * The ID of the character
     */
    public @NotNull String id() {
      return this.id;
    }

    public @NotNull Fragments fragments() {
      return this.fragments;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public ResponseFieldMarshaller marshaller() {
      return new ResponseFieldMarshaller() {
        @Override
        public void marshal(ResponseWriter writer) {
          writer.writeString($responseFields[0], __typename);
          writer.writeCustom((ResponseField.CustomTypeField) $responseFields[1], id);
          fragments.marshaller().marshal(writer);
        }
      };
    }

    @Override
    public String toString() {
      if ($toString == null) {
        $toString = "Hero{"
          + "__typename=" + __typename + ", "
          + "id=" + id + ", "
          + "fragments=" + fragments
          + "}";
      }
      return $toString;
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) {
        return true;
      }
      if (o instanceof Hero) {
        Hero that = (Hero) o;
        return this.__typename.equals(that.__typename)
         && this.id.equals(that.id)
         && this.fragments.equals(that.fragments);
      }
      return false;
    }

    @Override
    public int hashCode() {
      if (!$hashCodeMemoized) {
        int h = 1;
        h *= 1000003;
        h ^= __typename.hashCode();
        h *= 1000003;
        h ^= id.hashCode();
        h *= 1000003;
        h ^= fragments.hashCode();
        $hashCode = h;
        $hashCodeMemoized = true;
      }
      return $hashCode;
    }

    public static class Fragments {
      final Optional<HeroDetails> heroDetails;

      final Optional<HumanDetails> humanDetails;

      private transient volatile String $toString;

      private transient volatile int $hashCode;

      private transient volatile boolean $hashCodeMemoized;

      public Fragments(@Nullable HeroDetails heroDetails, @Nullable HumanDetails humanDetails) {
        this.heroDetails = Optional.fromNullable(heroDetails);
        this.humanDetails = Optional.fromNullable(humanDetails);
      }

      public Optional<HeroDetails> heroDetails() {
        return this.heroDetails;
      }

      public Optional<HumanDetails> humanDetails() {
        return this.humanDetails;
      }

      public ResponseFieldMarshaller marshaller() {
        return new ResponseFieldMarshaller() {
          @Override
          public void marshal(ResponseWriter writer) {
            final HeroDetails $heroDetails = heroDetails.isPresent() ? heroDetails.get() : null;
            if ($heroDetails != null) {
              writer.writeFragment($heroDetails.marshaller());
            }
            final HumanDetails $humanDetails = humanDetails.isPresent() ? humanDetails.get() : null;
            if ($humanDetails != null) {
              writer.writeFragment($humanDetails.marshaller());
            }
          }
        };
      }

      @Override
      public String toString() {
        if ($toString == null) {
          $toString = "Fragments{"
            + "heroDetails=" + heroDetails + ", "
            + "humanDetails=" + humanDetails
            + "}";
        }
        return $toString;
      }

      @Override
      public boolean equals(Object o) {
        if (o == this) {
          return true;
        }
        if (o instanceof Fragments) {
          Fragments that = (Fragments) o;
          return this.heroDetails.equals(that.heroDetails)
           && this.humanDetails.equals(that.humanDetails);
        }
        return false;
      }

      @Override
      public int hashCode() {
        if (!$hashCodeMemoized) {
          int h = 1;
          h *= 1000003;
          h ^= heroDetails.hashCode();
          h *= 1000003;
          h ^= humanDetails.hashCode();
          $hashCode = h;
          $hashCodeMemoized = true;
        }
        return $hashCode;
      }

      public static final class Mapper implements ResponseFieldMapper<Fragments> {
        static final ResponseField[] $responseFields = {
          ResponseField.forFragment("__typename", "__typename", Arrays.<ResponseField.Condition>asList(
            ResponseField.Condition.booleanCondition("withDetails", false),
            ResponseField.Condition.booleanCondition("skipHumanDetails", true)
          )),
          ResponseField.forFragment("__typename", "__typename", Arrays.<ResponseField.Condition>asList(
            ResponseField.Condition.booleanCondition("withDetails", false),
            ResponseField.Condition.typeCondition(new String[] {"Human"})
          ))
        };

        final HeroDetails.Mapper heroDetailsFieldMapper = new HeroDetails.Mapper();

        final HumanDetails.Mapper humanDetailsFieldMapper = new HumanDetails.Mapper();

        @Override
        public @NotNull Fragments map(ResponseReader reader) {
          final HeroDetails heroDetails = reader.readFragment($responseFields[0], new ResponseReader.ObjectReader<HeroDetails>() {
            @Override
            public HeroDetails read(ResponseReader reader) {
              return heroDetailsFieldMapper.map(reader);
            }
          });
          final HumanDetails humanDetails = reader.readFragment($responseFields[1], new ResponseReader.ObjectReader<HumanDetails>() {
            @Override
            public HumanDetails read(ResponseReader reader) {
              return humanDetailsFieldMapper.map(reader);
            }
          });
          return new Fragments(heroDetails, humanDetails);
        }
      }
    }

    public static final class Mapper implements ResponseFieldMapper<Hero> {
      final Fragments.Mapper fragmentsFieldMapper = new Fragments.Mapper();

      @Override
      public Hero map(ResponseReader reader) {
        final String __typename = reader.readString($responseFields[0]);
        final String id = reader.readCustomType((ResponseField.CustomTypeField) $responseFields[1]);
        final Fragments fragments = fragmentsFieldMapper.map(reader);
        return new Hero(__typename, id, fragments);
      }
    }
  }
}
