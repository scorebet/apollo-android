package com.apollographql.apollo.compiler

import java.io.File

class DefaultPackageNameProvider(rootFolders: Collection<String>, schemaFile: File, private val rootPackageName: String) : PackageNameProvider {
  private val roots = rootFolders.map(::File).map { File(it.absolutePath).normalize() }
  private val schemaPackageName = try {
    filePackageName(schemaFile.absolutePath)
  } catch (e: IllegalArgumentException) {
    // Can happen if the schema is not a child of roots
    ""
  }

  override val fragmentsPackageName = rootPackageName.appendPackageName(schemaPackageName).appendPackageName("fragment")
  override val typesPackageName = rootPackageName.appendPackageName(schemaPackageName).appendPackageName("type")

  override fun operationPackageName(filePath: String): String {
    return rootPackageName.appendPackageName(filePackageName(filePath))
  }

  private fun relativeToRoots(filePath: String): String {
    val file = File(File(filePath).absolutePath).normalize()
    roots.forEach { sourceDir ->
      try {
        val relative = file.toRelativeString(sourceDir)
        if (relative.startsWith(".."))
          return@forEach

        return relative
      } catch (e: IllegalArgumentException) {
      }
    }
    throw IllegalArgumentException("$filePath is not found in:\n${roots.joinToString("\n")}\n")
  }

  fun filePackageName(filePath: String): String {
    val relative = relativeToRoots(filePath)

    return relative
        .split(File.separator)
        .filter { it.isNotBlank() }
        .dropLast(1)
        .joinToString(".")
  }
}

fun String.appendPackageName(packageName: String) = "$this.$packageName".removePrefix(".").removeSuffix(".")
