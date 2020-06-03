package com.apollographql.apollo.api.internal

import kotlin.jvm.JvmStatic

object QueryDocumentMinifier {
  @JvmStatic
  fun minify(queryDocument: String): String {
    return queryDocument.replace("\\s *".toRegex(), " ")
  }
}
