package com.apollographql.apollo.compiler

import com.apollographql.apollo.api.OperationName
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.api.ScalarTypeAdapters
import com.apollographql.apollo.api.internal.OperationRequestBodyComposer
import com.apollographql.apollo.api.internal.QueryDocumentMinifier
import com.apollographql.apollo.api.internal.ResponseFieldMapper
import com.apollographql.apollo.api.internal.SimpleOperationResponseParser
import com.apollographql.apollo.compiler.VisitorSpec.VISITOR_CLASSNAME
import com.apollographql.apollo.compiler.ir.CodeGenerationContext
import com.apollographql.apollo.compiler.ir.CodeGenerator
import com.apollographql.apollo.compiler.ir.Fragment
import com.apollographql.apollo.compiler.ir.Operation
import com.apollographql.apollo.compiler.ir.Variable
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import okio.Buffer
import okio.BufferedSource
import okio.ByteString
import java.io.IOException
import javax.lang.model.element.Modifier

class OperationTypeSpecBuilder(
    val operation: Operation,
    val fragments: List<Fragment>,
    useSemanticNaming: Boolean
) : CodeGenerator {
  private val operationTypeName = operation.normalizedOperationName(useSemanticNaming)
  private val dataVarType = ClassName.get("", "$operationTypeName.Data")

  override fun toTypeSpec(context: CodeGenerationContext, abstract: Boolean): TypeSpec {
    val newContext = context.copy(reservedTypeNames = context.reservedTypeNames.plus(operationTypeName))
    return TypeSpec.classBuilder(operationTypeName)
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .addSuperinterface(operationSuperInterface(context))
        .applyIf(operation.description.isNotBlank()) { addJavadoc("\$L\n", operation.description) }
        .addOperationId(operation, newContext)
        .addQueryDocumentDefinition()
        .addConstructor(context)
        .addMethod(wrapDataMethod(context))
        .addVariablesDefinition(operation.variables, newContext)
        .addResponseFieldMapperMethod()
        .addBuilder(context)
        .addType(operation.toTypeSpec(newContext, abstract))
        .addOperationName()
        .addMethod(parseMethod(context))
        .addMethod(parseByteStringMethod(context))
        .addMethod(parseMethodWithDefaultScalarTypeAdapters(context))
        .addMethod(parseByteStringMethodWithDefaultScalarTypeAdapters(context))
        .addMethod(composeRequestBody())
        .addMethod(composeRequestBodyWithDefaultScalarTypeAdapters())
        .applyIf(operation.isQuery()) {
          addMethod(composeRequestBodyForQuery())
        }
        .build()
        .flatten(excludeTypeNames = listOf(
            VISITOR_CLASSNAME,
            Util.RESPONSE_FIELD_MAPPER_TYPE_NAME,
            (SchemaTypeSpecBuilder.FRAGMENTS_FIELD.type as ClassName).simpleName(),
            ClassNames.BUILDER.simpleName()
        ))
  }

  private fun operationSuperInterface(context: CodeGenerationContext): TypeName {
    val superInterfaceClassName = when {
      operation.isQuery() -> ClassNames.GRAPHQL_QUERY
      operation.isMutation() -> ClassNames.GRAPHQL_MUTATION
      operation.isSubscription() -> ClassNames.GRAPHQL_SUBSCRIPTION
      else -> throw IllegalArgumentException("Unknown operation type ${operation.operationType}")
    }

    return if (operation.variables.isNotEmpty()) {
      ParameterizedTypeName.get(superInterfaceClassName, dataVarType, wrapperType(context), variablesType())
    } else {
      ParameterizedTypeName.get(superInterfaceClassName, dataVarType, wrapperType(context),
          ClassNames.GRAPHQL_OPERATION_VARIABLES)
    }
  }

  private fun TypeSpec.Builder.addOperationId(operation: Operation, context: CodeGenerationContext): TypeSpec.Builder {
    val id = context.operationIdGenerator.apply(QueryDocumentMinifier.minify(operation.sourceWithFragments), operation.filePath)

    addField(FieldSpec.builder(ClassNames.STRING, OPERATION_ID_FIELD_NAME)
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
        .initializer("\$S", id)
        .build()
    )

    addMethod(MethodSpec.methodBuilder(OPERATION_ID_ACCESSOR_NAME)
        .addAnnotation(Annotations.OVERRIDE)
        .addModifiers(Modifier.PUBLIC)
        .returns(ClassNames.STRING)
        .addStatement("return $OPERATION_ID_FIELD_NAME")
        .build())

    return this
  }

  private fun TypeSpec.Builder.addQueryDocumentDefinition(): TypeSpec.Builder {
    addField(FieldSpec.builder(ClassNames.STRING, QUERY_DOCUMENT_FIELD_NAME)
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
        .initializer(
            CodeBlock.builder()
                .add("\$T.minify(\n", QueryDocumentMinifier::class.java)
                .indent()
                .add("\$S\n", operation.sourceWithFragments)
                .unindent()
                .add(")")
                .build()
        )
        .build()
    )

    addMethod(MethodSpec.methodBuilder(QUERY_DOCUMENT_ACCESSOR_NAME)
        .addAnnotation(Annotations.OVERRIDE)
        .addModifiers(Modifier.PUBLIC)
        .returns(ClassNames.STRING)
        .addStatement("return \$L", QUERY_DOCUMENT_FIELD_NAME)
        .build()
    )

    return this
  }

  private fun wrapDataMethod(context: CodeGenerationContext): MethodSpec {
    return MethodSpec.methodBuilder("wrapData")
        .addModifiers(Modifier.PUBLIC)
        .addAnnotation(Override::class.java)
        .addParameter(ParameterSpec.builder(dataVarType, "data").build())
        .returns(wrapperType(context))
        .addStatement(
            when {
              context.nullableValueType == NullableValueType.JAVA_OPTIONAL -> "return Optional.ofNullable(data)"
              context.nullableValueType != NullableValueType.ANNOTATED -> "return Optional.fromNullable(data)"
              else -> "return data"
            })
        .build()
  }

  private fun TypeSpec.Builder.addVariablesDefinition(variables: List<Variable>, context: CodeGenerationContext):
      TypeSpec.Builder {
    addField(FieldSpec.builder(variablesType(), VARIABLES_VAR)
        .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
        .build()
    )

    addMethod(MethodSpec.methodBuilder(VARIABLES_VAR)
        .addAnnotation(Annotations.OVERRIDE)
        .addModifiers(Modifier.PUBLIC)
        .returns(variablesType())
        .addStatement("return \$L", VARIABLES_VAR)
        .build()
    )

    if (variables.isNotEmpty()) {
      addType(VariablesTypeSpecBuilder(variables, context).build())
    }

    return this
  }

  private fun TypeSpec.Builder.addResponseFieldMapperMethod(): TypeSpec.Builder {
    return addMethod(MethodSpec.methodBuilder("responseFieldMapper")
        .addAnnotation(Annotations.OVERRIDE)
        .addModifiers(Modifier.PUBLIC)
        .returns(ParameterizedTypeName.get(ClassName.get(ResponseFieldMapper::class.java), dataVarType))
        .addStatement("return new \$L.\$L()", Operation.DATA_TYPE_NAME, Util.RESPONSE_FIELD_MAPPER_TYPE_NAME)
        .build())
  }

  private fun wrapperType(context: CodeGenerationContext) = when (context.nullableValueType) {
    NullableValueType.GUAVA_OPTIONAL -> ClassNames.parameterizedGuavaOptional(dataVarType)
    NullableValueType.APOLLO_OPTIONAL -> ClassNames.parameterizedOptional(dataVarType)
    NullableValueType.JAVA_OPTIONAL -> ClassNames.parameterizedJavaOptional(dataVarType)
    else -> dataVarType
  }

  private fun TypeSpec.Builder.addConstructor(context: CodeGenerationContext): TypeSpec.Builder {
    fun arguments(): List<ParameterSpec> {
      return operation.variables
          .map { variable ->
            variable.name.decapitalize() to JavaTypeResolver(
                context.copy(nullableValueType = NullableValueType.INPUT_TYPE),
                context.packageNameProvider.typesPackageName
            ).resolve(variable.type)
          }
          .map { (name, type) ->
            ParameterSpec.builder(type, name)
                .apply {
                  if (!type.annotations.contains(Annotations.NONNULL) && !type.isPrimitive) {
                    addAnnotation(Annotations.NONNULL)
                  }
                }
                .build()
          }
    }

    fun code(arguments: List<ParameterSpec>): CodeBlock {
      return if (arguments.isEmpty()) {
        CodeBlock.of("this.\$L = \$T.EMPTY_VARIABLES;\n", VARIABLES_VAR, ClassNames.GRAPHQL_OPERATION)
      } else {
        val codeBuilder = CodeBlock.builder()
        arguments.filter { !it.type.isPrimitive }.forEach {
          codeBuilder.addStatement("\$T.checkNotNull(\$L, \$S)", ClassNames.API_UTILS, it.name, "${it.name} == null")
        }
        codeBuilder.add("\$L = new \$T(", VARIABLES_VAR, variablesType())
        arguments.forEachIndexed { i, argument ->
          codeBuilder.add("\$L\$L", if (i > 0) ", " else "", argument.name)
        }
        codeBuilder.add(");\n").build()
      }
    }

    val arguments = arguments()
    return MethodSpec.constructorBuilder()
        .addModifiers(Modifier.PUBLIC)
        .addParameters(arguments)
        .addCode(code(arguments))
        .build()
        .let { addMethod(it) }
  }

  private fun TypeSpec.Builder.addBuilder(context: CodeGenerationContext): TypeSpec.Builder {
    addMethod(BuilderTypeSpecBuilder.builderFactoryMethod())

    if (operation.variables.isEmpty()) {
      return addType(BuilderTypeSpecBuilder(
          targetObjectClassName = ClassName.get("", operationTypeName),
          fields = emptyList(),
          fieldDefaultValues = emptyMap(),
          fieldJavaDocs = emptyMap(),
          typeDeclarations = context.typeDeclarations
      ).build())
    }

    operation.variables
        .map { it.name.decapitalize() to it.type }
        .map {
          it.first to JavaTypeResolver(
              context.copy(nullableValueType = NullableValueType.INPUT_TYPE),
              context.packageNameProvider.typesPackageName
          ).resolve(it.second)
        }
        .let {
          BuilderTypeSpecBuilder(
              targetObjectClassName = ClassName.get("", operationTypeName),
              fields = it,
              fieldDefaultValues = emptyMap(),
              fieldJavaDocs = emptyMap(),
              typeDeclarations = context.typeDeclarations
          )
        }
        .let { addType(it.build()) }

    return this
  }

  private fun variablesType() =
      if (operation.variables.isNotEmpty())
        ClassName.get("", "$operationTypeName.Variables")
      else
        ClassNames.GRAPHQL_OPERATION_VARIABLES

  private fun TypeSpec.Builder.addOperationName(): TypeSpec.Builder {
    fun operationNameTypeSpec() = TypeSpec.anonymousClassBuilder("")
        .addSuperinterface(OperationName::class.java)
        .addMethod(MethodSpec.methodBuilder("name")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override::class.java)
            .returns(java.lang.String::class.java)
            .addStatement("return \$S", operation.operationName)
            .build())
        .build()

    fun TypeSpec.Builder.addOperationNameField(): TypeSpec.Builder =
        addField(FieldSpec.builder(OperationName::class.java, "OPERATION_NAME", Modifier.PUBLIC, Modifier.STATIC,
            Modifier.FINAL)
            .initializer("\$L", operationNameTypeSpec())
            .build())

    fun TypeSpec.Builder.addOperationNameAccessor(): TypeSpec.Builder =
        addMethod(MethodSpec.methodBuilder("name")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override::class.java)
            .returns(OperationName::class.java)
            .addStatement("return \$L", "OPERATION_NAME")
            .build())

    return addOperationNameField()
        .addOperationNameAccessor()
  }

  private fun parseMethod(context: CodeGenerationContext): MethodSpec {
    return MethodSpec
        .methodBuilder("parse")
        .addAnnotation(Annotations.OVERRIDE)
        .addAnnotation(Annotations.NONNULL)
        .addModifiers(Modifier.PUBLIC)
        .addParameter(ParameterSpec
            .builder(BufferedSource::class.java, "source", Modifier.FINAL)
            .addAnnotation(Annotations.NONNULL)
            .build()
        )
        .addParameter(ParameterSpec
            .builder(ScalarTypeAdapters::class.java, "scalarTypeAdapters", Modifier.FINAL)
            .addAnnotation(Annotations.NONNULL)
            .build()
        )
        .addException(IOException::class.java)
        .returns(ParameterizedTypeName.get(ClassName.get(Response::class.java), wrapperType(context)))
        .addStatement("return \$T.parse(source, this, scalarTypeAdapters)", SimpleOperationResponseParser::class.java)
        .build()
  }

  private fun parseMethodWithDefaultScalarTypeAdapters(context: CodeGenerationContext): MethodSpec {
    return MethodSpec
        .methodBuilder("parse")
        .addAnnotation(Annotations.OVERRIDE)
        .addAnnotation(Annotations.NONNULL)
        .addModifiers(Modifier.PUBLIC)
        .addParameter(ParameterSpec
            .builder(BufferedSource::class.java, "source", Modifier.FINAL)
            .addAnnotation(Annotations.NONNULL)
            .build()
        )
        .addException(IOException::class.java)
        .returns(ParameterizedTypeName.get(ClassName.get(Response::class.java), wrapperType(context)))
        .addStatement("return parse(source, \$T.DEFAULT)", ScalarTypeAdapters::class.java)
        .build()
  }

  private fun parseByteStringMethod(context: CodeGenerationContext): MethodSpec {
    return MethodSpec
        .methodBuilder("parse")
        .addAnnotation(Annotations.OVERRIDE)
        .addAnnotation(Annotations.NONNULL)
        .addModifiers(Modifier.PUBLIC)
        .addParameter(ParameterSpec
            .builder(ByteString::class.java, "byteString", Modifier.FINAL)
            .addAnnotation(Annotations.NONNULL)
            .build()
        )
        .addParameter(ParameterSpec
            .builder(ScalarTypeAdapters::class.java, "scalarTypeAdapters", Modifier.FINAL)
            .addAnnotation(Annotations.NONNULL)
            .build()
        )
        .addException(IOException::class.java)
        .returns(ParameterizedTypeName.get(ClassName.get(Response::class.java), wrapperType(context)))
        .addStatement("return parse(new \$T().write(byteString), scalarTypeAdapters)", Buffer::class.java)
        .build()
  }

  private fun parseByteStringMethodWithDefaultScalarTypeAdapters(context: CodeGenerationContext): MethodSpec {
    return MethodSpec
        .methodBuilder("parse")
        .addAnnotation(Annotations.OVERRIDE)
        .addAnnotation(Annotations.NONNULL)
        .addModifiers(Modifier.PUBLIC)
        .addParameter(ParameterSpec
            .builder(ByteString::class.java, "byteString", Modifier.FINAL)
            .addAnnotation(Annotations.NONNULL)
            .build()
        )
        .addException(IOException::class.java)
        .returns(ParameterizedTypeName.get(ClassName.get(Response::class.java), wrapperType(context)))
        .addStatement("return parse(byteString, \$T.DEFAULT)", ScalarTypeAdapters::class.java)
        .build()
  }

  private fun composeRequestBody(): MethodSpec {
    return MethodSpec
        .methodBuilder("composeRequestBody")
        .addAnnotation(Annotations.OVERRIDE)
        .addAnnotation(Annotations.NONNULL)
        .addModifiers(Modifier.PUBLIC)
        .addParameter(ParameterSpec
            .builder(ScalarTypeAdapters::class.java, "scalarTypeAdapters", Modifier.FINAL)
            .addAnnotation(Annotations.NONNULL)
            .build()
        )
        .returns(ByteString::class.java)
        .addStatement("return \$T.compose(this, false, true, scalarTypeAdapters)", OperationRequestBodyComposer::class.java)
        .build()
  }

  private fun composeRequestBodyWithDefaultScalarTypeAdapters(): MethodSpec {
    return MethodSpec
        .methodBuilder("composeRequestBody")
        .addAnnotation(Annotations.NONNULL)
        .addAnnotation(Annotations.OVERRIDE)
        .addModifiers(Modifier.PUBLIC)
        .returns(ByteString::class.java)
        .addStatement("return \$T.compose(this, false, true, \$T.DEFAULT)", OperationRequestBodyComposer::class.java,
            ScalarTypeAdapters::class.java)
        .build()
  }

  private fun composeRequestBodyForQuery(): MethodSpec {
    return MethodSpec
        .methodBuilder("composeRequestBody")
        .addAnnotation(Annotations.OVERRIDE)
        .addAnnotation(Annotations.NONNULL)
        .addModifiers(Modifier.PUBLIC)
        .addParameter(ParameterSpec
            .builder(TypeName.BOOLEAN, "autoPersistQueries", Modifier.FINAL)
            .build()
        )
        .addParameter(ParameterSpec
            .builder(TypeName.BOOLEAN, "withQueryDocument", Modifier.FINAL)
            .build()
        )
        .addParameter(ParameterSpec
            .builder(ScalarTypeAdapters::class.java, "scalarTypeAdapters", Modifier.FINAL)
            .addAnnotation(Annotations.NONNULL)
            .build()
        )
        .returns(ByteString::class.java)
        .addStatement("return \$T.compose(this, autoPersistQueries, withQueryDocument, scalarTypeAdapters)",
            OperationRequestBodyComposer::class.java)
        .build()
  }

  companion object {
    private const val QUERY_DOCUMENT_FIELD_NAME = "QUERY_DOCUMENT"
    private const val OPERATION_ID_FIELD_NAME = "OPERATION_ID"
    private const val QUERY_DOCUMENT_ACCESSOR_NAME = "queryDocument"
    private const val OPERATION_ID_ACCESSOR_NAME = "operationId"
    private const val VARIABLES_VAR = "variables"
  }
}
