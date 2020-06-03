package com.apollographql.apollo.gradle.test

import com.apollographql.apollo.gradle.internal.child
import com.apollographql.apollo.gradle.util.TestUtils
import com.apollographql.apollo.gradle.util.TestUtils.withSimpleProject
import com.apollographql.apollo.gradle.util.generatedChild
import com.apollographql.apollo.gradle.util.replaceInText
import org.gradle.testkit.runner.TaskOutcome
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class UpToDateTests {
  @Test
  fun `complete test`() {
    withSimpleProject { dir ->
      `builds successfully and generates expected outputs`(dir)
      `nothing changed, task up to date`(dir)
      `adding a custom type to the build script re-generates the CustomType class`(dir)
    }
  }

  private fun `builds successfully and generates expected outputs`(dir: File) {
    val result = TestUtils.executeTask("generateApolloSources", dir)

    assertEquals(TaskOutcome.SUCCESS, result.task(":generateApolloSources")!!.outcome)

    // Java classes generated successfully
    assertTrue(dir.generatedChild("main/service/com/example/DroidDetailsQuery.java").isFile)
    assertTrue(dir.generatedChild("main/service/com/example/FilmsQuery.java").isFile)
    assertTrue(dir.generatedChild("main/service/com/example/fragment/SpeciesInformation.java").isFile)

    // verify that the custom type generated was Object.class because no customType mapping was specified
    TestUtils.assertFileContains(dir, "main/service/com/example/type/CustomType.java", "return \"java.lang.Object\";")

    // Optional is not added to the generated classes
    assert(!TestUtils.fileContains(dir, "main/service/com/example/DroidDetailsQuery.java", "Optional"))
    TestUtils.assertFileContains(dir, "main/service/com/example/DroidDetailsQuery.java", "import org.jetbrains.annotations.Nullable;")
  }

  fun `nothing changed, task up to date`(dir: File) {
    val result = TestUtils.executeTask("generateApolloSources", dir)

    assertEquals(TaskOutcome.UP_TO_DATE, result.task(":generateApolloSources")!!.outcome)

    // Java classes generated successfully
    assertTrue(dir.generatedChild("main/service/com/example/DroidDetailsQuery.java").isFile)
    assertTrue(dir.generatedChild("main/service/com/example/FilmsQuery.java").isFile)
    assertTrue(dir.generatedChild("main/service/com/example/fragment/SpeciesInformation.java").isFile)
  }

  fun `adding a custom type to the build script re-generates the CustomType class`(dir: File) {
    val apolloBlock = """
      apollo {
        customTypeMapping = ["DateTime": "java.util.Date"]
      }
    """.trimIndent()

    File(dir, "build.gradle").appendText(apolloBlock)

    val result = TestUtils.executeTask("generateApolloSources", dir)

    // modifying the customTypeMapping should cause the task to be out of date
    // and the task should run again
    assertEquals(TaskOutcome.SUCCESS, result.task(":generateApolloSources")!!.outcome)

    TestUtils.assertFileContains(dir, "main/service/com/example/type/CustomType.java", "return \"java.util.Date\";")

    val text = File(dir, "build.gradle").readText()
    File(dir, "build.gradle").writeText(text.replace(apolloBlock, ""))
  }

  @Test
  fun `change graphql file rebuilds the sources`() {
    withSimpleProject { dir ->
      var result = TestUtils.executeTask("generateApolloSources", dir, "-i")

      assertEquals(TaskOutcome.SUCCESS, result.task(":generateApolloSources")!!.outcome)
      assertThat(dir.generatedChild("main/service/com/example/DroidDetailsQuery.java").readText(), containsString("classification"))

      dir.child("src", "main", "graphql", "com", "example", "DroidDetails.graphql").replaceInText("classification", "")

      result = TestUtils.executeTask("generateApolloSources", dir, "-i")

      assertEquals(TaskOutcome.SUCCESS, result.task(":generateApolloSources")!!.outcome)
      assertThat(dir.generatedChild("main/service/com/example/DroidDetailsQuery.java").readText(), not(containsString("classification")))
    }
  }

  @Test
  fun `change schema file rebuilds the sources`() {
    withSimpleProject { dir ->
      var result = TestUtils.executeTask("generateApolloSources", dir, "-i")

      assertEquals(TaskOutcome.SUCCESS, result.task(":generateApolloSources")!!.outcome)

      val schemaFile = dir.child("src", "main", "graphql", "com", "example", "schema.json")
      schemaFile.writeText(schemaFile.readText() + "fezfze\n\n")

      result = TestUtils.executeTask("generateApolloSources", dir, "-i")

      assertEquals(TaskOutcome.SUCCESS, result.task(":generateApolloSources")!!.outcome)
    }
  }
}
