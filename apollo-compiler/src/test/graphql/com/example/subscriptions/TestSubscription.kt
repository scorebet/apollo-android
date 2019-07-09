// AUTO-GENERATED FILE. DO NOT MODIFY.
//
// This class was automatically generated by Apollo GraphQL plugin from the GraphQL queries it found.
// It should not be modified by hand.
//
package com.example.subscriptions

import com.apollographql.apollo.api.InputFieldMarshaller
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.OperationName
import com.apollographql.apollo.api.ResponseField
import com.apollographql.apollo.api.ResponseFieldMapper
import com.apollographql.apollo.api.ResponseFieldMarshaller
import com.apollographql.apollo.api.ResponseReader
import com.apollographql.apollo.api.Subscription
import kotlin.Any
import kotlin.Array
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.collections.Map
import kotlin.jvm.Transient

@Suppress("NAME_SHADOWING", "LocalVariableName", "RemoveExplicitTypeArguments",
    "NestedLambdaShadowedImplicitParameter")
data class TestSubscription(
  val repo: String
) : Subscription<TestSubscription.Data, TestSubscription.Data, Operation.Variables> {
  @Transient
  private val variables: Operation.Variables = object : Operation.Variables() {
    override fun valueMap(): Map<String, Any?> = mutableMapOf<String, Any?>().apply {
      this["repo"] = repo
    }

    override fun marshaller(): InputFieldMarshaller = InputFieldMarshaller { writer ->
      writer.writeString("repo", repo)
    }
  }

  override fun operationId(): String = OPERATION_ID
  override fun queryDocument(): String = QUERY_DOCUMENT
  override fun wrapData(data: Data?): Data? = data
  override fun variables(): Operation.Variables = variables
  override fun name(): OperationName = OPERATION_NAME
  override fun responseFieldMapper(): ResponseFieldMapper<Data> = ResponseFieldMapper {
    Data(it)
  }

  data class CommentAdded(
    val __typename: String,
    /**
     * The SQL ID of this entry
     */
    val id: Int,
    /**
     * The text of the comment
     */
    val content: String
  ) {
    fun marshaller(): ResponseFieldMarshaller = ResponseFieldMarshaller {
      it.writeString(RESPONSE_FIELDS[0], __typename)
      it.writeInt(RESPONSE_FIELDS[1], id)
      it.writeString(RESPONSE_FIELDS[2], content)
    }

    companion object {
      private val RESPONSE_FIELDS: Array<ResponseField> = arrayOf(
          ResponseField.forString("__typename", "__typename", null, false, null),
          ResponseField.forInt("id", "id", null, false, null),
          ResponseField.forString("content", "content", null, false, null)
          )

      operator fun invoke(reader: ResponseReader): CommentAdded {
        val __typename = reader.readString(RESPONSE_FIELDS[0])
        val id = reader.readInt(RESPONSE_FIELDS[1])
        val content = reader.readString(RESPONSE_FIELDS[2])
        return CommentAdded(
          __typename = __typename,
          id = id,
          content = content
        )
      }
    }
  }

  data class Data(
    /**
     * Subscription fires on every comment added
     */
    val commentAdded: CommentAdded?
  ) : Operation.Data {
    override fun marshaller(): ResponseFieldMarshaller = ResponseFieldMarshaller {
      it.writeObject(RESPONSE_FIELDS[0], commentAdded?.marshaller())
    }

    companion object {
      private val RESPONSE_FIELDS: Array<ResponseField> = arrayOf(
          ResponseField.forObject("commentAdded", "commentAdded", mapOf<String, Any>(
            "repoFullName" to mapOf<String, Any>(
              "kind" to "Variable",
              "variableName" to "repo")), true, null)
          )

      operator fun invoke(reader: ResponseReader): Data {
        val commentAdded = reader.readObject<CommentAdded>(RESPONSE_FIELDS[0]) { reader ->
          CommentAdded(reader)
        }

        return Data(
          commentAdded = commentAdded
        )
      }
    }
  }

  companion object {
    const val OPERATION_ID: String =
        "f140c0e88b739f3c0b1c105d981b7f8e2780689b3ed8a9faf2d7ee8184a0cf25"

    val QUERY_DOCUMENT: String = """
        |subscription TestSubscription(${'$'}repo: String!) {
        |  commentAdded(repoFullName: ${'$'}repo) {
        |    __typename
        |    id
        |    content
        |  }
        |}
        """.trimMargin()

    val OPERATION_NAME: OperationName = OperationName { "TestSubscription" }
  }
}
