package com.apollographql.apollo.compiler

import com.apollographql.apollo.api.internal.QueryDocumentMinifier
import com.apollographql.apollo.compiler.ApolloMetadata.Companion.merge
import com.apollographql.apollo.compiler.codegen.kotlin.GraphQLKompiler
import com.apollographql.apollo.compiler.ir.CodeGenerationContext
import com.apollographql.apollo.compiler.ir.CodeGenerationIR
import com.apollographql.apollo.compiler.ir.Field
import com.apollographql.apollo.compiler.ir.IRBuilder
import com.apollographql.apollo.compiler.ir.ScalarType
import com.apollographql.apollo.compiler.ir.SourceLocation
import com.apollographql.apollo.compiler.ir.TypeDeclaration.Companion.KIND_ENUM
import com.apollographql.apollo.compiler.ir.TypeDeclaration.Companion.KIND_INPUT_OBJECT_TYPE
import com.apollographql.apollo.compiler.operationoutput.OperationDescriptor
import com.apollographql.apollo.compiler.operationoutput.toJson
import com.apollographql.apollo.compiler.parser.error.DocumentParseException
import com.apollographql.apollo.compiler.parser.error.ParseException
import com.apollographql.apollo.compiler.parser.graphql.DocumentParseResult
import com.apollographql.apollo.compiler.parser.graphql.GraphQLDocumentParser
import com.apollographql.apollo.compiler.parser.introspection.IntrospectionSchema
import com.apollographql.apollo.compiler.parser.introspection.IntrospectionSchema.Companion.toIntrospectionSchema
import com.apollographql.apollo.compiler.parser.introspection.IntrospectionSchema.Companion.wrap
import com.apollographql.apollo.compiler.parser.sdl.GraphSdlSchema
import com.apollographql.apollo.compiler.parser.sdl.toIntrospectionSchema
import com.squareup.javapoet.JavaFile
import com.squareup.kotlinpoet.asClassName
import java.io.File

class GraphQLCompiler(val logger: Logger = NoOpLogger) {

  interface Logger {
    fun warning(message: String)
  }

  fun write(args: Arguments) {
    args.outputDir.deleteRecursively()
    args.outputDir.mkdirs()

    val roots = Roots(args.rootFolders)
    val metadata = collectMetadata(args.metadata, args.rootProjectDir)

    val (introspectionSchema, schemaPackageName) = getSchemaInfo(roots, args.rootPackageName, args.schemaFile, metadata)

    val generateKotlinModels = metadata?.generateKotlinModels ?: args.generateKotlinModels
    val userCustomTypesMap = metadata?.customTypesMap ?: args.customTypeMap

    val packageNameProvider = DefaultPackageNameProvider(
        roots = roots,
        rootPackageName = args.rootPackageName,
        schemaPackageName = schemaPackageName,
        packageName = args.packageName
    )

    val files = args.graphqlFiles
    checkDuplicateFiles(roots, files)

    val parseResult = GraphQLDocumentParser(
        schema = introspectionSchema,
        packageNameProvider = packageNameProvider
    ).parse(files)

    if (args.warnOnDeprecatedUsages) {
      val deprecatedUsages = parseResult.collectDeprecatedUsages()
      deprecatedUsages.forEach {
        // antlr is 0-indexed but IntelliJ is 1-indexed. Add 1 so that clicking the link will land on the correct location
        val column = it.sourceLocation.position + 1
        // Using this format, IntelliJ will parse the warning and display it in the 'run' panel
        logger.warning("w: ${it.filePath}:${it.sourceLocation.line}:${column}: ApolloGraphQL: Use of deprecated field '${it.field.fieldName}'")
      }
      if (args.failOnWarnings && deprecatedUsages.isNotEmpty()) {
        throw IllegalStateException("ApolloGraphQL: Warnings found and 'failOnWarnings' is true, aborting.")
      }
    }

    val ir = IRBuilder(
        schema = introspectionSchema,
        schemaPackageName = schemaPackageName,
        incomingMetadata = metadata,
        alwaysGenerateTypesMatching = args.alwaysGenerateTypesMatching,
        generateMetadata = args.generateMetadata
    ).build(parseResult)

    val operationOutput = ir.operations.map {
      OperationDescriptor(
          name = it.operationName,
          packageName = it.packageName,
          filePath = it.filePath,
          source = QueryDocumentMinifier.minify(it.sourceWithFragments)
      )
    }.let {
      args.operationOutputGenerator.generate(it)
    }

    check(operationOutput.size == ir.operations.size) {
      """The number of operation IDs (${operationOutput.size}) should match the number of operations (${ir.operations.size}).
        |Check that all your IDs are unique.
      """.trimMargin()
    }

    if (args.operationOutputFile != null) {
      args.operationOutputFile.writeText(operationOutput.toJson("  "))
    }

    val customTypeMap = (introspectionSchema.types.values.filter {
      it is IntrospectionSchema.Type.Scalar && ScalarType.forName(it.name) == null
    }.map { it.name } + ScalarType.ID.name)
        .supportedTypeMap(userCustomTypesMap, generateKotlinModels)

    if (generateKotlinModels) {
      GraphQLKompiler(
          ir = ir,
          customTypeMap = customTypeMap,
          operationOutput = operationOutput,
          useSemanticNaming = args.useSemanticNaming,
          generateAsInternal = args.generateAsInternal,
          kotlinMultiPlatformProject = args.kotlinMultiPlatformProject,
          enumAsSealedClassPatternFilters = args.enumAsSealedClassPatternFilters.map { it.toRegex() }
      ).write(args.outputDir)
    } else {
      val context = CodeGenerationContext(
          reservedTypeNames = emptyList(),
          typeDeclarations = ir.typeDeclarations,
          customTypeMap = customTypeMap,
          operationOutput = operationOutput,
          nullableValueType = args.nullableValueType,
          ir = ir,
          useSemanticNaming = args.useSemanticNaming,
          generateModelBuilder = args.generateModelBuilder,
          useJavaBeansSemanticNaming = args.useJavaBeansSemanticNaming,
          suppressRawTypesWarning = args.suppressRawTypesWarning,
          generateVisitorForPolymorphicDatatypes = args.generateVisitorForPolymorphicDatatypes
      )

      ir.writeJavaFiles(
          context = context,
          outputDir = args.outputDir
      )
    }

    args.metadataOutputFile.parentFile.mkdirs()
    if (args.generateMetadata) {
      val outgoingMetadata = ApolloMetadata(
          schema = if (metadata == null) introspectionSchema.wrap() else null,
          schemaPackageName = schemaPackageName,
          moduleName = args.moduleName,
          types = ir.enumsToGenerate + ir.inputObjectsToGenerate,
          fragments = ir.fragments.filter { ir.fragmentsToGenerate.contains(it.fragmentName) },
          generateKotlinModels = generateKotlinModels,
          customTypesMap = args.customTypeMap,
          pluginVersion = com.apollographql.apollo.compiler.VERSION
      ).let {
        if (args.rootProjectDir != null) {
          it.withRelativeFragments(args.rootProjectDir)
        } else {
          it
        }
      }
      outgoingMetadata.writeTo(args.metadataOutputFile)
    } else {
      // write a dummy metadata because the file is required as part as the `assemble` target
      args.metadataOutputFile.writeText("")
    }
  }

  private class DeprecatedUsage(val filePath: String, val sourceLocation: SourceLocation, val field: Field)

  private fun DocumentParseResult.collectDeprecatedUsages(): List<DeprecatedUsage> {
    return operations.flatMap { it.fields.collectDeprecatedUsages(it.filePath) } +
        fragments.flatMap { it.fields.collectDeprecatedUsages(it.filePath) }
  }

  /**
   * walk the list and return any deprecated fields
   * TODO: add support for deprecated enums
   */
  private fun List<Field>.collectDeprecatedUsages(filePath: String): List<DeprecatedUsage> {
    val fieldsToVisit = mutableListOf<Field>()
    val deprecatedUsages = mutableListOf<DeprecatedUsage>()
    fieldsToVisit.addAll(this)
    while (fieldsToVisit.isNotEmpty()) {
      val field = fieldsToVisit.removeAt(fieldsToVisit.lastIndex)
      if (field.deprecationReason != null) {
        deprecatedUsages.add(DeprecatedUsage(filePath, field.sourceLocation, field))
      }
      fieldsToVisit.addAll(field.fields)
    }
    return deprecatedUsages
  }

  private fun idClassName(generateKotlinModels: Boolean) = if (generateKotlinModels) {
    String::class.asClassName().toString()
  } else {
    ClassNames.STRING.toString()
  }

  private fun anyClassName(generateKotlinModels: Boolean) = if (generateKotlinModels) {
    Any::class.asClassName().toString()
  } else {
    ClassNames.OBJECT.toString()
  }

  private fun List<String>.supportedTypeMap(customTypeMap: Map<String, String>, generateKotlinModels: Boolean): Map<String, String> {
    return associate {
      val userClassName = customTypeMap[it]
      val className = when {
        userClassName != null -> userClassName
        // map ID to String by default
        it == ScalarType.ID.name -> idClassName(generateKotlinModels)
        // unknown scalars will be mapped to Object/Any
        else -> anyClassName(generateKotlinModels)
      }

      it to className
    }
  }


  private fun CodeGenerationIR.writeJavaFiles(context: CodeGenerationContext, outputDir: File) {
    fragments.filter {
      fragmentsToGenerate.contains(it.fragmentName)
    }.forEach {
      val typeSpec = it.toTypeSpec(context.copy())
      JavaFile
          .builder(it.packageName, typeSpec)
          .addFileComment(AUTO_GENERATED_FILE)
          .build()
          .writeTo(outputDir)
    }

    typeDeclarations.filter {
      (it.kind == KIND_INPUT_OBJECT_TYPE || it.kind == KIND_ENUM)
          && (enumsToGenerate + inputObjectsToGenerate).contains(it.name)
    }.forEach {
      val typeSpec = it.toTypeSpec(context.copy())
      JavaFile
          .builder(context.ir.typesPackageName, typeSpec)
          .addFileComment(AUTO_GENERATED_FILE)
          .build()
          .writeTo(outputDir)
    }

    if (scalarsToGenerate.isNotEmpty()) {
      val typeSpec = CustomEnumTypeSpecBuilder(context.copy(), scalarsToGenerate).build()
      JavaFile
          .builder(context.ir.typesPackageName, typeSpec)
          .addFileComment(AUTO_GENERATED_FILE)
          .build()
          .writeTo(outputDir)
    }

    operations.map { OperationTypeSpecBuilder(it, fragments, context.useSemanticNaming) }
        .forEach {
          val packageName = it.operation.packageName
          val typeSpec = it.toTypeSpec(context.copy())
          JavaFile
              .builder(packageName, typeSpec)
              .addFileComment(AUTO_GENERATED_FILE)
              .build()
              .writeTo(outputDir)
        }
  }

  companion object {
    private const val AUTO_GENERATED_FILE = "AUTO-GENERATED FILE. DO NOT MODIFY.\n\n" +
        "This class was automatically generated by Apollo GraphQL plugin from the GraphQL queries it found.\n" +
        "It should not be modified by hand.\n"

    private fun collectMetadata(metadata: List<File>, rootProjectDir: File?): ApolloMetadata? {
      return metadata.mapNotNull {
        ApolloMetadata.readFrom(it)?.let {
          if (rootProjectDir != null) {
            it.withResolvedFragments(rootProjectDir)
          } else {
            it
          }
        }
      }.merge()
    }

    private data class SchemaInfo(val introspectionSchema: IntrospectionSchema, val schemaPackageName: String)

    private fun getSchemaInfo(roots: Roots, rootPackageName: String, schemaFile: File?, metadata: ApolloMetadata?): SchemaInfo {
      check(schemaFile != null || metadata != null) {
        "ApolloGraphQL: cannot find schema.[json | sdl]"
      }
      check(schemaFile == null || metadata == null) {
        "ApolloGraphQL: You can't define a schema in ${schemaFile?.absolutePath} as one is already defined in a dependency. " +
            "Either remove the schema or the dependency"
      }

      if (schemaFile != null) {
        val introspectionSchema = if (schemaFile.extension == "json") {
          IntrospectionSchema(schemaFile)
        } else {
          try {
            GraphSdlSchema(schemaFile).toIntrospectionSchema()
          } catch (e: ParseException) {
            throw DocumentParseException(e, schemaFile.absolutePath)
          }
        }

        val packageName = try {
          roots.filePackageName(schemaFile.absolutePath)
        } catch (e: IllegalArgumentException) {
          // Can happen if the schema is not a child of roots
          ""
        }
        val schemaPackageName = "$rootPackageName.$packageName".removePrefix(".").removeSuffix(".")
        return SchemaInfo(introspectionSchema, schemaPackageName)
      } else if (metadata != null) {
        return SchemaInfo(metadata.schema!!.__schema.toIntrospectionSchema(), metadata.schemaPackageName!!)
      } else {
        throw IllegalStateException("There should at least be metadata or schemaFile")
      }
    }

    /**
     * Check for duplicates files. This can happen with Android variants
     */
    private fun checkDuplicateFiles(roots: Roots, files: Set<File>) {
      val map = files.groupBy { roots.filePackageName(it.normalize().absolutePath) to it.nameWithoutExtension }

      map.values.forEach {
        require(it.size == 1) {
          "ApolloGraphQL: duplicate(s) graphql file(s) found:\n" +
              it.map { it.absolutePath }.joinToString("\n")
        }
      }
    }
    val NoOpLogger = object: Logger {
      override fun warning(message: String) {
      }
    }
  }

  /**
   * For more details about the fields defined here, check the gradle plugin
   */
  data class Arguments(
      /**
       * The rootFolders where the graphqlFiles are located. The package name of each individual graphql query
       * will be the relative path to the root folders
       */
      val rootFolders: List<File>,
      /**
       * The files where the graphql queries/fragments are located
       */
      val graphqlFiles: Set<File>,
      /**
       * The schema. Can be either a SDL schema or an introspection schema.
       * If null, the schema, metedata must not be empty
       */
      val schemaFile: File?,
      /**
       * The folder where to generate the sources
       */
      val outputDir: File,

      //========== multi-module ============

      /**
       * A list of files containing metadata from previous compilations
       */
      val metadata: List<File> = emptyList(),
      /**
       * The moduleName for this metadata. Used for debugging purposes
       */
      val moduleName: String = "?",
      /**
       * Optional rootProjectDir. If it exists:
       * - when writing metadata, the compiler will output relative path to rootProjectDir
       * - when reading metadata, the compiler will lookup the actual file
       * This allows to lookup the real fragment file if all compilation units belong to the same project
       * and output nicer error messages
       */
      val rootProjectDir: File? = null,
      /**
       * The file where to write the metadata
       */
      val metadataOutputFile: File,
      val generateMetadata: Boolean = false,
      /**
       * Additional types to generate. This will generate this type and all types this type depends on.
       */
      val alwaysGenerateTypesMatching: Set<String>? = null,

      //========== operation-output ============

      /**
       * the file where to write the operationOutput
       * if null, no operationOutput is written
       */
      val operationOutputFile: File? = null,
      /**
       * the OperationOutputGenerator used to generate operation Ids
       */
      val operationOutputGenerator: OperationOutputGenerator = OperationOutputGenerator.DefaultOperationOuputGenerator(OperationIdGenerator.Sha256()),

      //========== global codegen options ============

      val rootPackageName: String = "",
      val generateKotlinModels: Boolean = false,
      val customTypeMap: Map<String, String> = emptyMap(),
      val useSemanticNaming: Boolean = true,
      val generateAsInternal: Boolean = false,
      val warnOnDeprecatedUsages: Boolean = true,
      val failOnWarnings: Boolean = false,
      val packageName: String? = null,

      //========== Kotlin codegen options ============

      val kotlinMultiPlatformProject: Boolean = false,
      val enumAsSealedClassPatternFilters: Set<String> = emptySet(),

      //========== Java codegen options ============

      val nullableValueType: NullableValueType = NullableValueType.ANNOTATED,
      val generateModelBuilder: Boolean = false,
      val useJavaBeansSemanticNaming: Boolean = false,
      val suppressRawTypesWarning: Boolean = false,
      val generateVisitorForPolymorphicDatatypes: Boolean = false
  )
}
