package com.apollographql.apollo.compiler

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

interface OperationIdGenerator {
  fun apply(
      /**
       * The GraphQL document
       */
      operationDocument: String,

      /**
       * The path to the GraphQL file
       */
      operationFilepath: String
  ): String

  /**
   * The version of the OperationIdGenerator
   *
   * You should change the version every time the implementation of the OperationIdGenerator
   * changes to let gradle and build tools know that they have to re-generate the
   * resulting files.
   */
  val version: String

  class Sha256 : OperationIdGenerator {
    override fun apply(operationDocument: String, operationFilepath: String): String {
      return operationDocument.sha256()
    }

    override val version = "sha256-1.0"

    private fun String.sha256(): String {
      val bytes = toByteArray(charset = StandardCharsets.UTF_8)
      val md = MessageDigest.getInstance("SHA-256")
      val digest = md.digest(bytes)
      return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
  }
}
