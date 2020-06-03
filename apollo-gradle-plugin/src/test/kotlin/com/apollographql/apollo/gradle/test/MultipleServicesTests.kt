package com.apollographql.apollo.gradle.test

import com.apollographql.apollo.gradle.internal.child
import com.apollographql.apollo.gradle.util.TestUtils
import com.apollographql.apollo.gradle.util.TestUtils.withProject
import com.apollographql.apollo.gradle.util.generatedChild
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MultipleServicesTests {
  fun withMultipleServicesProject(apolloConfiguration: String, block: (File) -> Unit) {
    withProject(usesKotlinDsl = false,
        plugins = listOf(TestUtils.javaPlugin, TestUtils.apolloPlugin),
        apolloConfiguration = apolloConfiguration) { dir ->
      val source = TestUtils.fixturesDirectory()

      val target = dir.child("src", "main", "graphql", "githunt")
      source.child("githunt").copyRecursively(target = target, overwrite = true)

      dir.child("src", "main", "graphql", "com").copyRecursively(target = dir.child("src", "main", "graphql", "starwars"), overwrite = true)
      dir.child("src", "main", "graphql", "com").deleteRecursively()

      block(dir)
    }
  }

  @Test
  fun `multiple schema files without using service is not supported`() {
    withMultipleServicesProject("") { dir ->
      var exception: Exception? = null
      try {
        TestUtils.executeTask("generateApolloSources", dir)
      } catch (e: UnexpectedBuildFailure) {
        exception = e
        assertThat(e.message, containsString("By default only one schema.json file is supported."))
      }
      assertNotNull(exception)
    }
  }

  @Test
  fun `can specify services explicitly`() {
    val apolloConfiguration = """
      apollo {
        service("starwars") {
          sourceFolder = "starwars"
        }
        service("githunt") {
          sourceFolder = "githunt"
        }
      }
    """.trimIndent()
    withMultipleServicesProject(apolloConfiguration) { dir ->
      TestUtils.executeTask("build", dir)

      assertTrue(dir.generatedChild("main/starwars/example/DroidDetailsQuery.java").isFile)
      assertTrue(dir.generatedChild("main/starwars/example/FilmsQuery.java").isFile)
      assertTrue(dir.generatedChild("main/starwars/example/fragment/SpeciesInformation.java").isFile)
      assertTrue(dir.generatedChild("main/githunt/FeedQuery.java").isFile)
      assertTrue(dir.generatedChild("main/githunt/fragment/RepositoryFragment.java").isFile)
    }
  }
}
