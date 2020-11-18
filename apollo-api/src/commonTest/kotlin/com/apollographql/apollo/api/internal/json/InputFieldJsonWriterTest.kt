package com.apollographql.apollo.api.internal.json

import com.apollographql.apollo.api.BigDecimal
import com.apollographql.apollo.api.CustomTypeAdapter
import com.apollographql.apollo.api.CustomTypeValue
import com.apollographql.apollo.api.ScalarType
import com.apollographql.apollo.api.ScalarTypeAdapters
import com.apollographql.apollo.api.internal.InputFieldMarshaller
import com.apollographql.apollo.api.internal.InputFieldWriter
import com.apollographql.apollo.api.toNumber
import okio.Buffer
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

class InputFieldJsonWriterTest {
  private val jsonBuffer = Buffer()
  private val jsonWriter = JsonWriter.of(jsonBuffer).apply {
    serializeNulls = true
    beginObject()
  }
  private val inputFieldJsonWriter = InputFieldJsonWriter(jsonWriter, ScalarTypeAdapters(emptyMap()))

  @Test
  fun writeString() {
    inputFieldJsonWriter.writeString("someField", "someValue")
    inputFieldJsonWriter.writeString("someNullField", null)
    assertEquals("{\"someField\":\"someValue\",\"someNullField\":null", jsonBuffer.readUtf8())
  }

  @Test
  fun writeInt() {
    inputFieldJsonWriter.writeInt("someField", 1)
    inputFieldJsonWriter.writeInt("someNullField", null)
    assertEquals("{\"someField\":1,\"someNullField\":null", jsonBuffer.readUtf8())
  }

  @Test
  fun writeLong() {
    inputFieldJsonWriter.writeLong("someField", 10L)
    inputFieldJsonWriter.writeLong("someNullField", null)
    assertEquals("{\"someField\":10,\"someNullField\":null", jsonBuffer.readUtf8())
  }

  @Test
  fun writeDouble() {
    inputFieldJsonWriter.writeDouble("someField", 1.01)
    inputFieldJsonWriter.writeDouble("someNullField", null)
    assertEquals("{\"someField\":1.01,\"someNullField\":null", jsonBuffer.readUtf8())
  }

  @Test
  fun writeNumber() {
    inputFieldJsonWriter.writeNumber("someField", BigDecimal("1.001").toNumber())
    inputFieldJsonWriter.writeNumber("someNullField", null)
    kotlin.test.assertEquals("{\"someField\":1.001,\"someNullField\":null", jsonBuffer.readUtf8())
  }

  @Test
  fun writeBoolean() {
    inputFieldJsonWriter.writeBoolean("someField", true)
    inputFieldJsonWriter.writeBoolean("someNullField", null)
    assertEquals("{\"someField\":true,\"someNullField\":null", jsonBuffer.readUtf8())
  }

  @Test
  fun writeObject() {
    inputFieldJsonWriter.writeObject("someField", object : InputFieldMarshaller {
      override fun marshal(writer: InputFieldWriter) {
        writer.writeString("someField", "someValue")
      }
    })
    inputFieldJsonWriter.writeObject("someNullField", null)
    assertEquals("{\"someField\":{\"someField\":\"someValue\"},\"someNullField\":null", jsonBuffer.readUtf8())
  }

  @Test
  fun writeList() {
    inputFieldJsonWriter.writeList("someField", object : InputFieldWriter.ListWriter {
      override fun write(listItemWriter: InputFieldWriter.ListItemWriter) {
        listItemWriter.writeString("someValue")
      }
    })
    inputFieldJsonWriter.writeList("someNullField", null)
    assertEquals("{\"someField\":[\"someValue\"],\"someNullField\":null", jsonBuffer.readUtf8())
  }

  @Test
  fun writeCustomBoolean() {
    val customTypeAdapters: MutableMap<ScalarType, CustomTypeAdapter<*>> = HashMap()
    val scalarType = MockCustomScalarType(CustomTypeValue.GraphQLBoolean::class, "com.apollographql.apollo.api.CustomTypeValue.GraphQLBoolean")
    customTypeAdapters[scalarType] = object : MockCustomTypeAdapter() {
      override fun encode(value: Any?): CustomTypeValue<*> {
        return CustomTypeValue.GraphQLBoolean((value as Boolean))
      }
    }
    val inputFieldJsonWriter = InputFieldJsonWriter(jsonWriter, ScalarTypeAdapters(customTypeAdapters))
    inputFieldJsonWriter.writeCustom("someField", scalarType, true)
    inputFieldJsonWriter.writeCustom("someNullField", scalarType, null)
    assertEquals("{\"someField\":true,\"someNullField\":null", jsonBuffer.readUtf8())
  }

  @Test
  fun writeCustomNumber() {
    val customTypeAdapters: MutableMap<ScalarType, CustomTypeAdapter<*>> = HashMap()
    val scalarType = MockCustomScalarType(CustomTypeValue.GraphQLNumber::class, "com.apollographql.apollo.api.CustomTypeValue.GraphQLNumber")
    customTypeAdapters[scalarType] = object : MockCustomTypeAdapter() {
      override fun encode(value: Any?): CustomTypeValue<*> {
        return CustomTypeValue.GraphQLNumber((value as BigDecimal).toNumber())
      }
    }
    val inputFieldJsonWriter = InputFieldJsonWriter(jsonWriter, ScalarTypeAdapters(customTypeAdapters))
    inputFieldJsonWriter.writeCustom("someField", scalarType, BigDecimal("100.1"))
    inputFieldJsonWriter.writeCustom("someNullField", scalarType, null)
    assertEquals("{\"someField\":100.1,\"someNullField\":null", jsonBuffer.readUtf8())
  }

  @Test
  fun writeCustomString() {
    val customTypeAdapters: MutableMap<ScalarType, CustomTypeAdapter<*>> = HashMap()
    val scalarType = MockCustomScalarType(CustomTypeValue.GraphQLString::class, "com.apollographql.apollo.api.CustomTypeValue.GraphQLString")
    customTypeAdapters[scalarType] = object : MockCustomTypeAdapter() {
      override fun encode(value: Any?): CustomTypeValue<*> {
        return CustomTypeValue.GraphQLString((value as String))
      }
    }
    val inputFieldJsonWriter = InputFieldJsonWriter(jsonWriter, ScalarTypeAdapters(customTypeAdapters))
    inputFieldJsonWriter.writeCustom("someField", scalarType, "someValue")
    inputFieldJsonWriter.writeCustom("someNullField", scalarType, null)
    assertEquals("{\"someField\":\"someValue\",\"someNullField\":null", jsonBuffer.readUtf8())
  }

  @Test
  fun writeCustomNull() {
    val customTypeAdapters: MutableMap<ScalarType, CustomTypeAdapter<*>> = HashMap()
    val scalarType = MockCustomScalarType(CustomTypeValue.GraphQLNumber::class, "com.apollographql.apollo.api.CustomTypeValue.GraphQLNull")
    customTypeAdapters[scalarType] = object : MockCustomTypeAdapter() {
      override fun encode(value: Any?): CustomTypeValue<*> {
        return CustomTypeValue.GraphQLNull
      }
    }
    val inputFieldJsonWriter = InputFieldJsonWriter(jsonWriter, ScalarTypeAdapters(customTypeAdapters))
    inputFieldJsonWriter.writeCustom("someField", scalarType, null)
    assertEquals("{\"someField\":null", jsonBuffer.readUtf8())
  }

  @Test
  fun writeCustomJsonObject() {
    val value = mapOf(
        "stringField" to "string",
        "booleanField" to true,
        "numberField" to 100,
        "listField" to listOf(
            "string",
            true,
            100,
            mapOf(
                "stringField" to "string",
                "numberField" to 100,
                "booleanField" to true,
                "listField" to listOf(1, 2, 3)
            )
        ),
        "objectField" to mapOf(
            "stringField" to "string",
            "numberField" to 100,
            "booleanField" to true,
            "listField" to listOf(1, 2, 3)
        )
    )
    val scalarType = MockCustomScalarType(Map::class, "kotlin.collections.Map")
    inputFieldJsonWriter.writeCustom("someField", scalarType, value)
    inputFieldJsonWriter.writeCustom("someNullField", scalarType, null)
    assertEquals("{\"someField\":{\"stringField\":\"string\",\"booleanField\":true,\"numberField\":100,\"listField\":[\"string\",true,100,{\"stringField\":\"string\",\"numberField\":100,\"booleanField\":true,\"listField\":[1,2,3]}],\"objectField\":{\"stringField\":\"string\",\"numberField\":100,\"booleanField\":true,\"listField\":[1,2,3]}},\"someNullField\":null", jsonBuffer.readUtf8())
  }

  @Test
  fun writeCustomList() {
    val value = listOf(
        "string",
        true,
        100,
        mapOf(
            "stringField" to "string",
            "numberField" to 100,
            "booleanField" to true,
            "listField" to listOf(1, 2, 3)
        )
    )
    val scalarType = MockCustomScalarType(List::class, "kotlin.collections.List")
    inputFieldJsonWriter.writeCustom("someField", scalarType, value)
    inputFieldJsonWriter.writeCustom("someNullField", scalarType, null)
    assertEquals("{\"someField\":[\"string\",true,100,{\"stringField\":\"string\",\"numberField\":100,\"booleanField\":true,\"listField\":[1,2,3]}],\"someNullField\":null", jsonBuffer.readUtf8())
  }

  @Test
  fun writeListOfList() {
    inputFieldJsonWriter.writeList("someField", object : InputFieldWriter.ListWriter {
      override fun write(listItemWriter: InputFieldWriter.ListItemWriter) {
        listItemWriter.writeList(object : InputFieldWriter.ListWriter {
          override fun write(listItemWriter: InputFieldWriter.ListItemWriter) {
            listItemWriter.writeString("someValue")
          }
        })
      }
    })
    inputFieldJsonWriter.writeList("someNullField", null)
    assertEquals("{\"someField\":[[\"someValue\"]],\"someNullField\":null", jsonBuffer.readUtf8())
  }

  private data class MockCustomScalarType internal constructor(val clazz: KClass<*>, val qualifiedName: String) : ScalarType {
    override fun typeName(): String {
      return clazz.simpleName!!
    }

    override fun className(): String {
      return qualifiedName
    }
  }

  private abstract inner class MockCustomTypeAdapter : CustomTypeAdapter<Any?> {
    override fun decode(value: CustomTypeValue<*>): Any {
      throw UnsupportedOperationException()
    }
  }
}
