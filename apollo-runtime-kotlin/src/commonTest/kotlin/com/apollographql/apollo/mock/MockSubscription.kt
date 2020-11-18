package com.apollographql.apollo.mock

import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.OperationName
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.api.ScalarTypeAdapters
import com.apollographql.apollo.api.Subscription
import com.apollographql.apollo.api.internal.ResponseFieldMapper
import com.apollographql.apollo.api.internal.ResponseFieldMarshaller
import okio.BufferedSource
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

internal class MockSubscription : Subscription<MockSubscription.Data, MockSubscription.Data, Operation.Variables> {

  override fun composeRequestBody(
      autoPersistQueries: Boolean,
      withQueryDocument: Boolean,
      scalarTypeAdapters: ScalarTypeAdapters): ByteString {
    return composeRequestBody()
  }

  override fun composeRequestBody(scalarTypeAdapters: ScalarTypeAdapters): ByteString {
    return composeRequestBody()
  }

  override fun composeRequestBody(): ByteString {
    return """
    { 
      "operationName": "MockSubscription",
      "query": "subscription MockSubscription { name }",
      "variables": "{"key": "value"}"
    }
    """.trimIndent().encodeUtf8()
  }

  override fun queryDocument(): String = "subscription MockSubscription { name }"

  override fun variables(): Operation.Variables = Operation.EMPTY_VARIABLES

  override fun responseFieldMapper(): ResponseFieldMapper<Data> {
    throw UnsupportedOperationException("Unsupported")
  }

  override fun wrapData(data: Data?): Data? = data

  override fun name(): OperationName = object : OperationName {
    override fun name(): String = "MockSubscription"
  }

  override fun operationId(): String = "MockSubscription".hashCode().toString()

  override fun parse(source: BufferedSource, scalarTypeAdapters: ScalarTypeAdapters): Response<Data> {
    return Response(
        operation = this,
        data = Data(source.readUtf8())
    )
  }

  override fun parse(byteString: ByteString, scalarTypeAdapters: ScalarTypeAdapters): Response<Data> {
    return Response(
        operation = this,
        data = Data(byteString.toString())
    )
  }

  override fun parse(source: BufferedSource): Response<Data> {
    return parse(source, ScalarTypeAdapters.DEFAULT)
  }

  override fun parse(byteString: ByteString): Response<Data> {
    return parse(byteString, ScalarTypeAdapters.DEFAULT)
  }

  class Data(val rawResponse: String) : Operation.Data {
    override fun marshaller(): ResponseFieldMarshaller {
      throw UnsupportedOperationException("Unsupported")
    }
  }
}
