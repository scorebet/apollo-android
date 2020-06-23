package com.apollographql.apollo.internal.response

import com.apollographql.apollo.api.BigDecimal
import com.apollographql.apollo.api.CustomTypeAdapter
import com.apollographql.apollo.api.CustomTypeValue.Companion.fromRawValue
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.ResponseField
import com.apollographql.apollo.api.ScalarType
import com.apollographql.apollo.api.ScalarTypeAdapters
import com.apollographql.apollo.api.internal.FieldValueResolver
import com.apollographql.apollo.api.internal.ResolveDelegate
import com.apollographql.apollo.api.internal.ResponseReader
import java.util.Collections

class RealResponseReader<R>(
    val operationVariables: Operation.Variables,
    private val recordSet: R,
    internal val fieldValueResolver: FieldValueResolver<R>,
    internal val scalarTypeAdapters: ScalarTypeAdapters,
    internal val resolveDelegate: ResolveDelegate<R>
) : ResponseReader {

  private val variableValues: Map<String, Any?> = operationVariables.valueMap()

  override fun readString(field: ResponseField): String? {
    if (shouldSkip(field)) {
      return null
    }
    val value = fieldValueResolver.valueFor<String>(recordSet, field)
    checkValue(field, value)
    willResolve(field, value)
    if (value == null) {
      resolveDelegate.didResolveNull()
    } else {
      resolveDelegate.didResolveScalar(value)
    }
    didResolve(field)
    return value
  }

  override fun readInt(field: ResponseField): Int? {
    if (shouldSkip(field)) {
      return null
    }
    val value = fieldValueResolver.valueFor<BigDecimal>(recordSet, field)
    checkValue(field, value)
    willResolve(field, value)
    if (value == null) {
      resolveDelegate.didResolveNull()
    } else {
      resolveDelegate.didResolveScalar(value)
    }
    didResolve(field)
    return value?.toInt()
  }

  override fun readLong(field: ResponseField): Long? {
    if (shouldSkip(field)) {
      return null
    }
    val value = fieldValueResolver.valueFor<BigDecimal>(recordSet, field)
    checkValue(field, value)
    willResolve(field, value)
    if (value == null) {
      resolveDelegate.didResolveNull()
    } else {
      resolveDelegate.didResolveScalar(value)
    }
    didResolve(field)
    return value?.toLong()
  }

  override fun readDouble(field: ResponseField): Double? {
    if (shouldSkip(field)) {
      return null
    }
    val value = fieldValueResolver.valueFor<BigDecimal>(recordSet, field)
    checkValue(field, value)
    willResolve(field, value)
    if (value == null) {
      resolveDelegate.didResolveNull()
    } else {
      resolveDelegate.didResolveScalar(value)
    }
    didResolve(field)
    return value?.toDouble()
  }

  override fun readBoolean(field: ResponseField): Boolean? {
    if (shouldSkip(field)) {
      return null
    }
    val value = fieldValueResolver.valueFor<Boolean>(recordSet, field)
    checkValue(field, value)
    willResolve(field, value)
    if (value == null) {
      resolveDelegate.didResolveNull()
    } else {
      resolveDelegate.didResolveScalar(value)
    }
    didResolve(field)
    return value
  }

  override fun <T : Any> readObject(field: ResponseField, objectReader: ResponseReader.ObjectReader<T>): T? {
    if (shouldSkip(field)) {
      return null
    }
    val value: R? = fieldValueResolver.valueFor(recordSet, field)
    checkValue(field, value)
    willResolve(field, value)
    resolveDelegate.willResolveObject(field, value)
    val parsedValue: T?
    parsedValue = if (value == null) {
      resolveDelegate.didResolveNull()
      null
    } else {
      objectReader.read(RealResponseReader(operationVariables, value, fieldValueResolver, scalarTypeAdapters, resolveDelegate))
    }
    resolveDelegate.didResolveObject(field, value)
    didResolve(field)
    return parsedValue
  }

  override fun <T : Any> readList(field: ResponseField, listReader: ResponseReader.ListReader<T>): List<T?>? {
    if (shouldSkip(field)) {
      return null
    }
    val values = fieldValueResolver.valueFor<List<*>>(recordSet, field)
    checkValue(field, values)
    willResolve(field, values)
    val result = if (values == null) {
      resolveDelegate.didResolveNull()
      null
    } else {
      values.mapIndexed { index, value ->
        resolveDelegate.willResolveElement(index)
        if (value == null) {
          resolveDelegate.didResolveNull()
          null
        } else {
          listReader.read(ListItemReader(field, value))
        }.also { resolveDelegate.didResolveElement(index) }
      }.also { resolveDelegate.didResolveList(values) }
    }
    didResolve(field)
    return if (result != null) Collections.unmodifiableList(result) else null
  }

  override fun <T : Any> readCustomType(field: ResponseField.CustomTypeField): T? {
    if (shouldSkip(field)) {
      return null
    }
    val value = fieldValueResolver.valueFor<Any>(recordSet, field)
    checkValue(field, value)
    willResolve(field, value)
    val result: T?
    if (value == null) {
      resolveDelegate.didResolveNull()
      result = null
    } else {
      val typeAdapter: CustomTypeAdapter<T> = scalarTypeAdapters.adapterFor(field.scalarType)
      result = typeAdapter.decode(fromRawValue(value))
      checkValue(field, result)
      resolveDelegate.didResolveScalar(value)
    }
    didResolve(field)
    return result
  }

  override fun <T : Any> readFragment(field: ResponseField, objectReader: ResponseReader.ObjectReader<T>): T? {
    if (shouldSkip(field)) {
      return null
    }
    val value = fieldValueResolver.valueFor<String>(recordSet, field)
    checkValue(field, value)
    willResolve(field, value)
    return if (value == null) {
      resolveDelegate.didResolveNull()
      didResolve(field)
      null
    } else {
      resolveDelegate.didResolveScalar(value)
      didResolve(field)
      if (field.type === ResponseField.Type.FRAGMENT) {
        for (condition in field.conditions) {
          if (condition is ResponseField.TypeNameCondition) {
            if (!condition.typeNames.contains(value)) {
              return null
            }
          }
        }
        objectReader.read(this)
      } else {
        null
      }
    }
  }

  private fun shouldSkip(field: ResponseField): Boolean {
    for (condition in field.conditions) {
      if (condition is ResponseField.BooleanCondition) {
        val conditionValue = variableValues[condition.variableName] as Boolean?
        if (condition.inverted) {
          // means it's a skip directive
          if (conditionValue == true) {
            return true
          }
        } else {
          // means it's an include directive
          if (conditionValue == false) {
            return true
          }
        }
      }
    }
    return false
  }

  private fun willResolve(field: ResponseField, value: Any?) {
    resolveDelegate.willResolve(field, operationVariables, value)
  }

  private fun didResolve(field: ResponseField) {
    resolveDelegate.didResolve(field, operationVariables)
  }

  private fun checkValue(field: ResponseField, value: Any?) {
    check(field.optional || value != null) {
      "corrupted response reader, expected non null value for ${field.fieldName}"
    }
  }

  private inner class ListItemReader(
      private val field: ResponseField,
      private val value: Any
  ) : ResponseReader.ListItemReader {

    override fun readString(): String {
      resolveDelegate.didResolveScalar(value)
      return value as String
    }

    override fun readInt(): Int {
      resolveDelegate.didResolveScalar(value)
      return (value as BigDecimal).toInt()
    }

    override fun readLong(): Long {
      resolveDelegate.didResolveScalar(value)
      return (value as BigDecimal).toLong()
    }

    override fun readDouble(): Double {
      resolveDelegate.didResolveScalar(value)
      return (value as BigDecimal).toDouble()
    }

    override fun readBoolean(): Boolean {
      resolveDelegate.didResolveScalar(value)
      return value as Boolean
    }

    override fun <T : Any> readCustomType(scalarType: ScalarType): T {
      val typeAdapter: CustomTypeAdapter<T> = scalarTypeAdapters.adapterFor(scalarType)
      resolveDelegate.didResolveScalar(value)
      return typeAdapter.decode(fromRawValue(value))
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> readObject(objectReader: ResponseReader.ObjectReader<T>): T {
      val value = value as R
      resolveDelegate.willResolveObject(field, value)
      val item = objectReader.read(RealResponseReader(operationVariables, value, fieldValueResolver, scalarTypeAdapters, resolveDelegate))
      resolveDelegate.didResolveObject(field, value)
      return item
    }

    override fun <T : Any> readList(listReader: ResponseReader.ListReader<T>): List<T?> {
      val values = value as List<*>
      val result = values.mapIndexed { index, value ->
        resolveDelegate.willResolveElement(index)
        if (value == null) {
          resolveDelegate.didResolveNull()
          null
        } else {
          listReader.read(ListItemReader(field, value))
        }.also { resolveDelegate.didResolveElement(index) }
      }
      resolveDelegate.didResolveList(values)
      return Collections.unmodifiableList(result)
    }
  }
}
