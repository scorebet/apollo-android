package com.apollographql.apollo.api.internal

import com.apollographql.apollo.api.CustomTypeAdapter
import com.apollographql.apollo.api.CustomTypeValue
import com.apollographql.apollo.api.EMPTY_OPERATION
import com.apollographql.apollo.api.ResponseField
import com.apollographql.apollo.api.ScalarType
import com.apollographql.apollo.api.ScalarTypeAdapters
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URL
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date

class SimpleResponseReaderJvmCustomTypesTest {
  private val noConditions: List<ResponseField.Condition> = emptyList()

  @Test
  fun readCustom() {
    val successField = ResponseField.forCustomType("successFieldResponseName", "successFieldName", null,
        false, DATE_CUSTOM_TYPE, noConditions)
    val classCastExceptionField = ResponseField.forCustomType("classCastExceptionField",
        "classCastExceptionField", null, false, DATE_CUSTOM_TYPE, noConditions)
    val recordSet: MutableMap<String, Any> = HashMap()
    recordSet["successFieldResponseName"] = "2017-04-16"
    recordSet["successFieldName"] = "2018-04-16"
    recordSet["classCastExceptionField"] = 0
    val responseReader = responseReader(recordSet)
    assertEquals(DATE_TIME_FORMAT.parse("2017-04-16"), responseReader.readCustomType<Date>(successField))
    try {
      responseReader.readCustomType<Any>(classCastExceptionField)
      Assert.fail("expected ClassCastException")
    } catch (expected: ClassCastException) {
      // expected
    }
  }

  @Test
  fun readCustomList() {
    val successField = ResponseField.forList("successFieldResponseName", "successFieldName", null, false, noConditions)
    val recordSet: MutableMap<String, Any> = HashMap()
    recordSet["successFieldResponseName"] = listOf("2017-04-16", "2017-04-17", "2017-04-18")
    recordSet["successFieldName"] = listOf("2017-04-19", "2017-04-20")
    recordSet["classCastExceptionField"] = "anything"
    val responseReader = responseReader(recordSet)
    assertEquals(
        listOf(DATE_TIME_FORMAT.parse("2017-04-16"), DATE_TIME_FORMAT.parse("2017-04-17"), DATE_TIME_FORMAT.parse("2017-04-18")),
        responseReader.readList(successField) { reader -> reader.readCustomType<Date>(DATE_CUSTOM_TYPE) }
    )
  }

  @Test
  fun optionalFieldsIOException() {
    val responseReader = responseReader(emptyMap())
    responseReader.readCustomType<Any>(ResponseField.forCustomType("customTypeField", "customTypeField", null, true, DATE_CUSTOM_TYPE,
        noConditions))
  }

  @Test
  fun mandatoryFieldsIOException() {
    val responseReader = responseReader(emptyMap())
    try {
      responseReader.readCustomType<Any>(ResponseField.forCustomType("customTypeField", "customTypeField", null, false, DATE_CUSTOM_TYPE,
          noConditions))
      Assert.fail("expected NullPointerException")
    } catch (expected: NullPointerException) {
      //expected
    }
  }

  companion object {
    private fun responseReader(recordSet: Map<String, Any>): SimpleResponseReader {
      val customTypeAdapters: MutableMap<ScalarType, CustomTypeAdapter<*>> = HashMap()
      customTypeAdapters[DATE_CUSTOM_TYPE] = object : CustomTypeAdapter<Any?> {
        override fun decode(value: CustomTypeValue<*>): Any {
          return try {
            DATE_TIME_FORMAT.parse(value.value.toString())
          } catch (e: ParseException) {
            throw ClassCastException()
          }
        }

        override fun encode(value: Any?): CustomTypeValue<*> {
          throw UnsupportedOperationException()
        }
      }
      customTypeAdapters[URL_CUSTOM_TYPE] = object : CustomTypeAdapter<Any?> {
        override fun decode(value: CustomTypeValue<*>): Any {
          throw UnsupportedOperationException()
        }

        override fun encode(value: Any?): CustomTypeValue<*> {
          throw UnsupportedOperationException()
        }
      }
      customTypeAdapters[OBJECT_CUSTOM_TYPE] = object : CustomTypeAdapter<Any?> {
        override fun decode(value: CustomTypeValue<*>): Any {
          return value.value.toString()
        }

        override fun encode(value: Any?): CustomTypeValue<*> {
          throw UnsupportedOperationException()
        }
      }
      return SimpleResponseReader(recordSet, EMPTY_OPERATION.variables(), ScalarTypeAdapters(customTypeAdapters))
    }

    private val OBJECT_CUSTOM_TYPE: ScalarType = object : ScalarType {
      override fun typeName(): String {
        return String::class.java.name
      }

      override fun className(): String {
        return String::class.java.name
      }
    }
    private val DATE_CUSTOM_TYPE: ScalarType = object : ScalarType {
      override fun typeName(): String {
        return Date::class.java.name
      }

      override fun className(): String {
        return Date::class.java.name
      }
    }
    private val URL_CUSTOM_TYPE: ScalarType = object : ScalarType {
      override fun typeName(): String {
        return URL::class.java.name
      }

      override fun className(): String {
        return URL::class.java.name
      }
    }
    private val DATE_TIME_FORMAT = SimpleDateFormat("yyyyy-mm-dd")
  }
}
