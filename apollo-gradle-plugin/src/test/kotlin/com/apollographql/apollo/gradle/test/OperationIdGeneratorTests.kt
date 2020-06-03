package com.apollographql.apollo.gradle.test

import com.apollographql.apollo.gradle.util.TestUtils
import com.apollographql.apollo.gradle.util.TestUtils.withSimpleProject
import com.apollographql.apollo.gradle.util.generatedChild
import com.apollographql.apollo.gradle.util.replaceInText
import org.gradle.testkit.runner.TaskOutcome
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Test
import java.io.File

class CustomIdGeneratorTests {
  val apolloConfiguration = """
      class MyIdGenerator implements OperationIdGenerator {
          String apply(String queryString, String queryFilepath) {
              return queryString.length().toString();
          }
          String version = "MyIdGenerator-v1"
      }
      
      apollo {
        operationIdGenerator = new MyIdGenerator()
      }
    """.trimIndent()

  @Test
  fun `up-to-date checks are working`() {
    withSimpleProject(apolloConfiguration = apolloConfiguration) {dir ->
      val gradleFile = File(dir, "build.gradle").readText()

      File(dir, "build.gradle").writeText("import com.apollographql.apollo.compiler.OperationIdGenerator\n$gradleFile")

      var result = TestUtils.executeTask("generateApolloSources", dir)

      Assert.assertEquals(TaskOutcome.SUCCESS, result.task(":generateApolloSources")!!.outcome)

      result = TestUtils.executeTask("generateApolloSources", dir)

      Assert.assertEquals(TaskOutcome.UP_TO_DATE, result.task(":generateApolloSources")!!.outcome)
    }
  }

  @Test
  fun `operationIdGenerator can be set from onCompilationUnit`() {
    val apolloConfiguration = """
      class MyIdGenerator implements OperationIdGenerator {
          String apply(String queryString, String queryFilepath) {
              return "someQueryId";
          }
          String version = "MyIdGenerator-v1"
      }
      
      apollo {
        onCompilationUnit {
          operationIdGenerator = new MyIdGenerator()
        }
      }
    """.trimIndent()

    withSimpleProject(apolloConfiguration = apolloConfiguration) {dir ->
      val gradleFile = File(dir, "build.gradle").readText()

      File(dir, "build.gradle").writeText("import com.apollographql.apollo.compiler.OperationIdGenerator\n$gradleFile")

      val result = TestUtils.executeTask("generateApolloSources", dir)

      Assert.assertEquals(TaskOutcome.SUCCESS, result.task(":generateApolloSources")!!.outcome)

      val queryJavaFile = dir.generatedChild("main/service/com/example/DroidDetailsQuery.java")
      Assert.assertThat(queryJavaFile.readText(), CoreMatchers.containsString("someQueryId"))
    }
  }

  @Test
  fun `changing the operationIdGenerator recompiles sources`() {
    withSimpleProject(apolloConfiguration = apolloConfiguration) { dir ->
      val gradleFile = File(dir, "build.gradle").readText()

      File(dir, "build.gradle").writeText("import com.apollographql.apollo.compiler.OperationIdGenerator\n$gradleFile")

      var result = TestUtils.executeTask("generateApolloSources", dir)

      Assert.assertEquals(TaskOutcome.SUCCESS, result.task(":generateApolloSources")!!.outcome)

      File(dir, "build.gradle").replaceInText("operationDocument.length()", "(operationDocument.length() * 2)")
      File(dir, "build.gradle").replaceInText("MyIdGenerator-v1", "MyIdGenerator-v2")

      result = TestUtils.executeTask("generateApolloSources", dir)

      Assert.assertEquals(TaskOutcome.SUCCESS, result.task(":generateApolloSources")!!.outcome)
    }
  }

  @Test
  fun `build cache is working as expected`() {
    withSimpleProject(apolloConfiguration = apolloConfiguration) { dir ->
      val buildCachePath = File(dir, "buildCache").absolutePath
      File(dir, "settings.gradle").appendText("""
        
        buildCache {
            local {
                directory = new File("$buildCachePath")
            }
        }
      """.trimIndent())

      val gradleFile = File(dir, "build.gradle").readText()

      File(dir, "build.gradle").writeText("import com.apollographql.apollo.compiler.OperationIdGenerator\n$gradleFile")

      var result = TestUtils.executeTask("generateMainServiceApolloSources", dir, "--build-cache", "-i")

      Assert.assertEquals(TaskOutcome.SUCCESS, result.task(":generateMainServiceApolloSources")!!.outcome)

      File(dir, "build").deleteRecursively()

      result = TestUtils.executeTask("generateMainServiceApolloSources", dir, "--build-cache", "-i")

      Assert.assertEquals(TaskOutcome.FROM_CACHE, result.task(":generateMainServiceApolloSources")!!.outcome)
    }
  }
}
