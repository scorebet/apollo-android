// AUTO-GENERATED FILE. DO NOT MODIFY.
//
// This class was automatically generated by Apollo GraphQL plugin from the GraphQL queries it found.
// It should not be modified by hand.
//
package com.example.input_object_type.type

import com.apollographql.apollo.api.ScalarType
import java.lang.Class
import kotlin.String

enum class CustomType : ScalarType {
  DATE {
    override fun typeName(): String = "Date"

    override fun javaType(): Class<*> = java.util.Date::class.java
  },

  ID {
    override fun typeName(): String = "ID"

    override fun javaType(): Class<*> = java.lang.Integer::class.java
  }
}
