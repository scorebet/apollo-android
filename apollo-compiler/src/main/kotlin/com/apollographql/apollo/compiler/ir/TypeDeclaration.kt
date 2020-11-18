package com.apollographql.apollo.compiler.ir

import com.apollographql.apollo.compiler.Annotations
import com.apollographql.apollo.compiler.ClassNames
import com.apollographql.apollo.compiler.InputTypeSpecBuilder
import com.apollographql.apollo.compiler.escapeJavaReservedWord
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.Modifier

data class TypeDeclaration(
    val kind: String,
    val name: String,
    val description: String = "",
    val values: List<TypeDeclarationValue> = emptyList(),
    val fields: List<TypeDeclarationField> = emptyList()
) : CodeGenerator {
  override fun toTypeSpec(context: CodeGenerationContext, abstract: Boolean): TypeSpec = when (kind) {
    KIND_ENUM -> enumTypeToTypeSpec()
    KIND_INPUT_OBJECT_TYPE -> inputObjectToTypeSpec(context)
    else -> throw UnsupportedOperationException("unsupported $kind type declaration")
  }

  private fun enumTypeToTypeSpec(): TypeSpec {
    val enumConstants = values.map { value ->
      value.name to TypeSpec.anonymousClassBuilder("\$S", value.name)
          .apply {
            if (!value.description.isNullOrEmpty()) {
              addJavadoc("\$L\n", value.description)
            }
          }
          .apply {
            if (value.deprecationReason != null) {
              addAnnotation(Annotations.DEPRECATED)
              if (!value.deprecationReason.isNullOrBlank()) {
                addJavadoc("@deprecated \$L\n", value.deprecationReason)
              }
            }
          }
          .build()
    }
    val unknownConstantTypeSpec = TypeSpec.anonymousClassBuilder("\$S", "\$UNKNOWN")
        .addJavadoc("\$L\n", "Auto generated constant for unknown enum values")
        .build()
    val safeValueOfMethodSpec = MethodSpec.methodBuilder(ENUM_SAFE_VALUE_OF)
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addParameter(ParameterSpec.builder(ClassNames.STRING, "rawValue").build())
        .returns(ClassName.get("", name.capitalize()))
        .addCode(CodeBlock.builder()
            .beginControlFlow("for (\$L enumValue : values())", name.capitalize())
            .beginControlFlow("if (enumValue.rawValue.equals(rawValue))")
            .addStatement("return enumValue")
            .endControlFlow()
            .endControlFlow()
            .addStatement("return \$L.\$L", name.capitalize(), ENUM_UNKNOWN_CONSTANT)
            .build()
        )
        .build()

    return TypeSpec.enumBuilder(name.capitalize())
        .addModifiers(Modifier.PUBLIC)
        .addField(FieldSpec.builder(ClassNames.STRING, "rawValue", Modifier.PRIVATE, Modifier.FINAL).build())
        .addMethod(MethodSpec.constructorBuilder()
            .addParameter(ParameterSpec.builder(ClassNames.STRING, "rawValue").build())
            .addStatement("this.rawValue = rawValue")
            .build()
        )
        .addMethod(MethodSpec.methodBuilder("rawValue")
            .addModifiers(Modifier.PUBLIC)
            .returns(ClassNames.STRING)
            .addStatement("return rawValue")
            .build())
        .apply {
          enumConstants.forEach { (name, typeSpec) ->
            addEnumConstant(name.escapeJavaReservedWord().toUpperCase(), typeSpec)
          }
        }
        .addEnumConstant(ENUM_UNKNOWN_CONSTANT, unknownConstantTypeSpec)
        .apply {
          if (description.isNotEmpty()) {
            addJavadoc("\$L\n", description)
          }
        }
        .addMethod(safeValueOfMethodSpec)
        .build()
  }

  private fun inputObjectToTypeSpec(context: CodeGenerationContext) =
      InputTypeSpecBuilder(name, fields, context).build()

  companion object {
    val KIND_INPUT_OBJECT_TYPE: String = "InputObjectType"
    val KIND_ENUM: String = "EnumType"
    val KIND_SCALAR_TYPE: String = "ScalarType"
    val ENUM_UNKNOWN_CONSTANT: String = "\$UNKNOWN"
    val ENUM_SAFE_VALUE_OF: String = "safeValueOf"
  }
}
