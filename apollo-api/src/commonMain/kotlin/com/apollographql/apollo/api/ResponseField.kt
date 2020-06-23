package com.apollographql.apollo.api

import kotlin.jvm.JvmStatic

/**
 * An abstraction for a field in a graphQL operation. Field can refer to:
 * - GraphQL
 * - Scalar Types,
 * - Objects
 * - List,
 * - etc.
 *
 * For a complete list of types that a Field object can refer to see [ResponseField.Type] class.
 */
open class ResponseField internal constructor(
    val type: Type,
    val responseName: String,
    val fieldName: String,
    val arguments: Map<String, Any?>,
    val optional: Boolean,
    val conditions: List<Condition>
) {

  @Deprecated(message = "Use property instead", replaceWith = ReplaceWith(expression = "type"))
  fun type(): Type {
    return type
  }

  @Deprecated(message = "Use property instead", replaceWith = ReplaceWith(expression = "responseName"))
  fun responseName(): String {
    return responseName
  }

  @Deprecated(message = "Use property instead", replaceWith = ReplaceWith(expression = "fieldName"))
  fun fieldName(): String {
    return fieldName
  }

  @Deprecated(message = "Use property instead", replaceWith = ReplaceWith(expression = "arguments"))
  fun arguments(): Map<String, Any?> {
    return arguments
  }

  @Deprecated(message = "Use property instead", replaceWith = ReplaceWith(expression = "optional"))
  fun optional(): Boolean {
    return optional
  }

  @Deprecated(message = "Use property instead", replaceWith = ReplaceWith(expression = "conditions"))
  fun conditions(): List<Condition> {
    return conditions
  }

  /**
   * Resolves field argument value by [name]. If argument represents a references to the variable, it will be resolved from
   * provided operation [variables] values.
   */
  @Suppress("UNCHECKED_CAST")
  fun resolveArgument(
      name: String,
      variables: Operation.Variables
  ): Any? {
    val variableValues = variables.valueMap()
    val argumentValue = arguments[name]
    return if (argumentValue is Map<*, *>) {
      val argumentValueMap = argumentValue as Map<String, Any?>
      if (isArgumentValueVariableType(argumentValueMap)) {
        val variableName = argumentValueMap[VARIABLE_NAME_KEY].toString()
        variableValues[variableName]
      } else {
        null
      }
    } else {
      argumentValue
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ResponseField) return false

    if (type != other.type) return false
    if (responseName != other.responseName) return false
    if (fieldName != other.fieldName) return false
    if (arguments != other.arguments) return false
    if (optional != other.optional) return false
    if (conditions != other.conditions) return false

    return true
  }

  override fun hashCode(): Int {
    var result = type.hashCode()
    result = 31 * result + responseName.hashCode()
    result = 31 * result + fieldName.hashCode()
    result = 31 * result + arguments.hashCode()
    result = 31 * result + optional.hashCode()
    result = 31 * result + conditions.hashCode()
    return result
  }

  /**
   * An abstraction for the field types
   */
  enum class Type {
    STRING, INT, LONG, DOUBLE, BOOLEAN, ENUM, OBJECT, LIST, CUSTOM, FRAGMENT, FRAGMENTS
  }

  /**
   * Abstraction for a Field representing a custom GraphQL scalar type.
   */
  class CustomTypeField internal constructor(
      responseName: String,
      fieldName: String,
      arguments: Map<String, Any?>?,
      optional: Boolean,
      conditions: List<Condition>?,
      val scalarType: ScalarType
  ) : ResponseField(
      type = Type.CUSTOM,
      responseName = responseName,
      fieldName = fieldName,
      arguments = arguments.orEmpty(),
      optional = optional,
      conditions = conditions.orEmpty()
  ) {

    @Deprecated(message = "Use property instead", replaceWith = ReplaceWith(expression = "scalarType"))
    fun scalarType(): ScalarType {
      return scalarType
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is CustomTypeField) return false
      if (!super.equals(other)) return false

      if (scalarType != other.scalarType) return false

      return true
    }

    override fun hashCode(): Int {
      var result = super.hashCode()
      result = 31 * result + scalarType.hashCode()
      return result
    }
  }

  /**
   * Abstraction for condition to be associated with field
   */
  open class Condition internal constructor() {
    companion object {
      /**
       * Creates new [TypeNameCondition] with provided [types] conditions.
       */
      @JvmStatic
      fun typeCondition(types: Array<String>): TypeNameCondition {
        return TypeNameCondition(listOf(*types))
      }

      /**
       * Creates new [BooleanCondition] for provided [variableName].
       */
      @JvmStatic
      fun booleanCondition(variableName: String, inverted: Boolean): BooleanCondition {
        return BooleanCondition(variableName, inverted)
      }
    }
  }

  /**
   * Abstraction for type name condition
   */
  class TypeNameCondition internal constructor(
      val typeNames: List<String>
  ) : Condition() {
    @Deprecated(message = "Use property instead", replaceWith = ReplaceWith(expression = "typeNames"))
    fun typeNames(): List<String> {
      return typeNames
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is TypeNameCondition) return false

      if (typeNames != other.typeNames) return false

      return true
    }

    override fun hashCode(): Int {
      return typeNames.hashCode()
    }
  }

  /**
   * Abstraction for boolean condition
   */
  class BooleanCondition internal constructor(
      val variableName: String,
      val inverted: Boolean
  ) : Condition() {
    @Deprecated(message = "Use property instead", replaceWith = ReplaceWith(expression = "variableName"))
    fun variableName(): String {
      return variableName
    }

    @Deprecated(message = "Use property instead", replaceWith = ReplaceWith(expression = "inverted"))
    fun inverted(): Boolean {
      return inverted
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is BooleanCondition) return false

      if (variableName != other.variableName) return false
      if (inverted != other.inverted) return false

      return true
    }

    override fun hashCode(): Int {
      var result = variableName.hashCode()
      result = 31 * result + inverted.hashCode()
      return result
    }
  }

  companion object {
    private const val VARIABLE_IDENTIFIER_KEY = "kind"
    private const val VARIABLE_IDENTIFIER_VALUE = "Variable"
    const val VARIABLE_NAME_KEY = "variableName"

    /**
     * Factory method for creating a Field instance representing [Type.STRING].
     *
     * @param responseName alias for the result of a field
     * @param fieldName name of the field in the GraphQL operation
     * @param arguments arguments to be passed along with the field
     * @param optional whether the arguments passed along are optional or required
     * @param conditions list of conditions for this field
     * @return Field instance representing [Type.STRING]
     */
    @JvmStatic
    fun forString(
        responseName: String,
        fieldName: String,
        arguments: Map<String, Any?>?,
        optional: Boolean,
        conditions: List<Condition>?
    ): ResponseField {
      return ResponseField(
          type = Type.STRING,
          responseName = responseName,
          fieldName = fieldName,
          arguments = arguments.orEmpty(),
          optional = optional,
          conditions = conditions.orEmpty()
      )
    }

    /**
     * Factory method for creating a Field instance representing [Type.INT].
     *
     * @param responseName alias for the result of a field
     * @param fieldName name of the field in the GraphQL operation
     * @param arguments arguments to be passed along with the field
     * @param optional whether the arguments passed along are optional or required
     * @param conditions list of conditions for this field
     * @return Field instance representing [Type.INT]
     */
    @JvmStatic
    fun forInt(
        responseName: String,
        fieldName: String,
        arguments: Map<String, Any?>?,
        optional: Boolean,
        conditions: List<Condition>?
    ): ResponseField {
      return ResponseField(
          type = Type.INT,
          responseName = responseName,
          fieldName = fieldName,
          arguments = arguments.orEmpty(),
          optional = optional,
          conditions = conditions.orEmpty()
      )
    }

    /**
     * Factory method for creating a Field instance representing [Type.LONG].
     *
     * @param responseName alias for the result of a field
     * @param fieldName name of the field in the GraphQL operation
     * @param arguments arguments to be passed along with the field
     * @param optional whether the arguments passed along are optional or required
     * @param conditions list of conditions for this field
     * @return Field instance representing [Type.LONG]
     */
    @JvmStatic
    fun forLong(
        responseName: String,
        fieldName: String,
        arguments: Map<String, Any?>?,
        optional: Boolean,
        conditions: List<Condition>?
    ): ResponseField {
      return ResponseField(
          type = Type.LONG,
          responseName = responseName,
          fieldName = fieldName,
          arguments = arguments.orEmpty(),
          optional = optional,
          conditions = conditions.orEmpty()
      )
    }

    /**
     * Factory method for creating a Field instance representing [Type.DOUBLE].
     *
     * @param responseName alias for the result of a field
     * @param fieldName name of the field in the GraphQL operation
     * @param arguments arguments to be passed along with the field
     * @param optional whether the arguments passed along are optional or required
     * @param conditions list of conditions for this field
     * @return Field instance representing [Type.DOUBLE]
     */
    @JvmStatic
    fun forDouble(
        responseName: String,
        fieldName: String,
        arguments: Map<String, Any?>?,
        optional: Boolean,
        conditions: List<Condition>?
    ): ResponseField {
      return ResponseField(
          type = Type.DOUBLE,
          responseName = responseName,
          fieldName = fieldName,
          arguments = arguments.orEmpty(),
          optional = optional,
          conditions = conditions.orEmpty()
      )
    }

    /**
     * Factory method for creating a Field instance representing [Type.BOOLEAN].
     *
     * @param responseName alias for the result of a field
     * @param fieldName name of the field in the GraphQL operation
     * @param arguments arguments to be passed along with the field
     * @param optional whether the arguments passed along are optional or required
     * @param conditions list of conditions for this field
     * @return Field instance representing [Type.BOOLEAN]
     */
    @JvmStatic
    fun forBoolean(
        responseName: String,
        fieldName: String,
        arguments: Map<String, Any?>?,
        optional: Boolean,
        conditions: List<Condition>?
    ): ResponseField {
      return ResponseField(
          type = Type.BOOLEAN,
          responseName = responseName,
          fieldName = fieldName,
          arguments = arguments.orEmpty(),
          optional = optional,
          conditions = conditions.orEmpty()
      )
    }

    /**
     * Factory method for creating a Field instance representing [Type.ENUM].
     *
     * @param responseName alias for the result of a field
     * @param fieldName name of the field in the GraphQL operation
     * @param arguments arguments to be passed along with the field
     * @param optional whether the arguments passed along are optional or required
     * @param conditions list of conditions for this field
     * @return Field instance representing [Type.ENUM]
     */
    @JvmStatic
    fun forEnum(
        responseName: String,
        fieldName: String,
        arguments: Map<String, Any?>?,
        optional: Boolean,
        conditions: List<Condition>?
    ): ResponseField {
      return ResponseField(
          type = Type.ENUM,
          responseName = responseName,
          fieldName = fieldName,
          arguments = arguments.orEmpty(),
          optional = optional,
          conditions = conditions.orEmpty()
      )
    }

    /**
     * Factory method for creating a Field instance representing a custom [Type.OBJECT].
     *
     * @param responseName alias for the result of a field
     * @param fieldName name of the field in the GraphQL operation
     * @param arguments arguments to be passed along with the field
     * @param optional whether the arguments passed along are optional or required
     * @param conditions list of conditions for this field
     * @return Field instance representing custom [Type.OBJECT]
     */
    @JvmStatic
    fun forObject(
        responseName: String,
        fieldName: String,
        arguments: Map<String, Any?>?,
        optional: Boolean,
        conditions: List<Condition>?
    ): ResponseField {
      return ResponseField(
          type = Type.OBJECT,
          responseName = responseName,
          fieldName = fieldName,
          arguments = arguments.orEmpty(),
          optional = optional,
          conditions = conditions.orEmpty()
      )
    }

    /**
     * Factory method for creating a Field instance representing [Type.LIST].
     *
     * @param responseName alias for the result of a field
     * @param fieldName name of the field in the GraphQL operation
     * @param arguments arguments to be passed along with the field
     * @param optional whether the arguments passed along are optional or required
     * @param conditions list of conditions for this field
     * @return Field instance representing [Type.LIST]
     */
    @JvmStatic
    fun forList(
        responseName: String,
        fieldName: String,
        arguments: Map<String, Any?>?,
        optional: Boolean,
        conditions: List<Condition>?
    ): ResponseField {
      return ResponseField(
          type = Type.LIST,
          responseName = responseName,
          fieldName = fieldName,
          arguments = arguments.orEmpty(),
          optional = optional,
          conditions = conditions.orEmpty()
      )
    }

    /**
     * Factory method for creating a Field instance representing a custom GraphQL Scalar type, [Type.CUSTOM]
     *
     * @param responseName alias for the result of a field
     * @param fieldName name of the field in the GraphQL operation
     * @param arguments arguments to be passed along with the field
     * @param optional whether the arguments passed along are optional or required
     * @param scalarType the custom scalar type of the field
     * @param conditions list of conditions for this field
     * @return Field instance representing [Type.CUSTOM]
     */
    @JvmStatic
    fun forCustomType(
        responseName: String,
        fieldName: String,
        arguments: Map<String, Any?>?,
        optional: Boolean,
        scalarType: ScalarType,
        conditions: List<Condition>?
    ): CustomTypeField {
      return CustomTypeField(
          responseName = responseName,
          fieldName = fieldName,
          arguments = arguments.orEmpty(),
          optional = optional,
          scalarType = scalarType,
          conditions = conditions.orEmpty()
      )
    }

    /**
     * Factory method for creating a Field instance representing [Type.FRAGMENT].
     *
     * @param responseName alias for the result of a field
     * @param fieldName name of the field in the GraphQL operation
     * @param conditions conditional GraphQL types
     * @return Field instance representing [Type.FRAGMENT]
     */
    @JvmStatic
    fun forFragment(
        responseName: String,
        fieldName: String,
        conditions: List<Condition>?
    ): ResponseField {
      return ResponseField(
          type = Type.FRAGMENT,
          responseName = responseName,
          fieldName = fieldName,
          arguments = emptyMap(),
          optional = false,
          conditions = conditions.orEmpty()
      )
    }

    @JvmStatic
    fun isArgumentValueVariableType(objectMap: Map<String, Any?>): Boolean {
      return (objectMap.containsKey(VARIABLE_IDENTIFIER_KEY)
          && objectMap[VARIABLE_IDENTIFIER_KEY] == VARIABLE_IDENTIFIER_VALUE && objectMap.containsKey(VARIABLE_NAME_KEY))
    }
  }
}
