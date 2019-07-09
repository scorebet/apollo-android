// AUTO-GENERATED FILE. DO NOT MODIFY.
//
// This class was automatically generated by Apollo GraphQL plugin from the GraphQL queries it found.
// It should not be modified by hand.
//
package com.example.custom_scalar_type

import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.OperationName
import com.apollographql.apollo.api.Query
import com.apollographql.apollo.api.ResponseField
import com.apollographql.apollo.api.ResponseFieldMapper
import com.apollographql.apollo.api.ResponseFieldMarshaller
import com.apollographql.apollo.api.ResponseReader
import com.example.custom_scalar_type.type.CustomType
import java.util.Date
import kotlin.Any
import kotlin.Array
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List

@Suppress("NAME_SHADOWING", "LocalVariableName", "RemoveExplicitTypeArguments",
    "NestedLambdaShadowedImplicitParameter")
class TestQuery : Query<TestQuery.Data, TestQuery.Data, Operation.Variables> {
  override fun operationId(): String = OPERATION_ID
  override fun queryDocument(): String = QUERY_DOCUMENT
  override fun wrapData(data: Data?): Data? = data
  override fun variables(): Operation.Variables = Operation.EMPTY_VARIABLES
  override fun name(): OperationName = OPERATION_NAME
  override fun responseFieldMapper(): ResponseFieldMapper<Data> = ResponseFieldMapper {
    Data(it)
  }

  data class Hero(
    val __typename: String,
    /**
     * The name of the character
     */
    val name: String,
    /**
     * The date character was born.
     */
    val birthDate: Date,
    /**
     * The dates of appearances
     */
    val appearanceDates: List<Date>,
    /**
     * The date character was born.
     */
    val fieldWithUnsupportedType: Any,
    /**
     * Profile link
     */
    val profileLink: java.lang.String,
    /**
     * Links
     */
    val links: List<java.lang.String>
  ) {
    fun marshaller(): ResponseFieldMarshaller = ResponseFieldMarshaller {
      it.writeString(RESPONSE_FIELDS[0], __typename)
      it.writeString(RESPONSE_FIELDS[1], name)
      it.writeCustom(RESPONSE_FIELDS[2] as ResponseField.CustomTypeField, birthDate)
      it.writeList(RESPONSE_FIELDS[3], appearanceDates) { value, listItemWriter ->
        value?.forEach { value ->
          listItemWriter.writeCustom(CustomType.DATE, value)
        }
      }
      it.writeCustom(RESPONSE_FIELDS[4] as ResponseField.CustomTypeField, fieldWithUnsupportedType)
      it.writeCustom(RESPONSE_FIELDS[5] as ResponseField.CustomTypeField, profileLink)
      it.writeList(RESPONSE_FIELDS[6], links) { value, listItemWriter ->
        value?.forEach { value ->
          listItemWriter.writeCustom(CustomType.URL, value)
        }
      }
    }

    companion object {
      private val RESPONSE_FIELDS: Array<ResponseField> = arrayOf(
          ResponseField.forString("__typename", "__typename", null, false, null),
          ResponseField.forString("name", "name", null, false, null),
          ResponseField.forCustomType("birthDate", "birthDate", null, false, CustomType.DATE, null),
          ResponseField.forList("appearanceDates", "appearanceDates", null, false, null),
          ResponseField.forCustomType("fieldWithUnsupportedType", "fieldWithUnsupportedType", null,
              false, CustomType.UNSUPPORTEDTYPE, null),
          ResponseField.forCustomType("profileLink", "profileLink", null, false, CustomType.URL,
              null),
          ResponseField.forList("links", "links", null, false, null)
          )

      operator fun invoke(reader: ResponseReader): Hero {
        val __typename = reader.readString(RESPONSE_FIELDS[0])
        val name = reader.readString(RESPONSE_FIELDS[1])
        val birthDate = reader.readCustomType<Date>(RESPONSE_FIELDS[2] as
            ResponseField.CustomTypeField)
        val appearanceDates = reader.readList<Date>(RESPONSE_FIELDS[3]) {
          it.readCustomType<Date>(CustomType.DATE)
        }
        val fieldWithUnsupportedType = reader.readCustomType<Any>(RESPONSE_FIELDS[4] as
            ResponseField.CustomTypeField)
        val profileLink = reader.readCustomType<java.lang.String>(RESPONSE_FIELDS[5] as
            ResponseField.CustomTypeField)
        val links = reader.readList<java.lang.String>(RESPONSE_FIELDS[6]) {
          it.readCustomType<java.lang.String>(CustomType.URL)
        }
        return Hero(
          __typename = __typename,
          name = name,
          birthDate = birthDate,
          appearanceDates = appearanceDates,
          fieldWithUnsupportedType = fieldWithUnsupportedType,
          profileLink = profileLink,
          links = links
        )
      }
    }
  }

  data class Data(
    val hero: Hero?
  ) : Operation.Data {
    override fun marshaller(): ResponseFieldMarshaller = ResponseFieldMarshaller {
      it.writeObject(RESPONSE_FIELDS[0], hero?.marshaller())
    }

    companion object {
      private val RESPONSE_FIELDS: Array<ResponseField> = arrayOf(
          ResponseField.forObject("hero", "hero", null, true, null)
          )

      operator fun invoke(reader: ResponseReader): Data {
        val hero = reader.readObject<Hero>(RESPONSE_FIELDS[0]) { reader ->
          Hero(reader)
        }

        return Data(
          hero = hero
        )
      }
    }
  }

  companion object {
    const val OPERATION_ID: String =
        "e7efe02b29424a4425cafac2dbd3374b420e11212cc5a00eb7ee28ec904fef69"

    val QUERY_DOCUMENT: String = """
        |query TestQuery {
        |  hero {
        |    __typename
        |    name
        |    birthDate
        |    appearanceDates
        |    fieldWithUnsupportedType
        |    profileLink
        |    links
        |  }
        |}
        """.trimMargin()

    val OPERATION_NAME: OperationName = OperationName { "TestQuery" }
  }
}
