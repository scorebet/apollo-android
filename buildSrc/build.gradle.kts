plugins {
  id("org.jetbrains.kotlin.jvm").version("1.4.10")
}

project.apply {
  from(file("../gradle/dependencies.gradle"))
}

repositories {
  gradlePluginPortal()
  google()
  jcenter()
  mavenCentral()
}

dependencies {
  implementation(groovy.util.Eval.x(project, "x.dep.okHttp.okHttp4"))
  implementation(groovy.util.Eval.x(project, "x.dep.moshi.moshi"))
  implementation(groovy.util.Eval.x(project, "x.dep.android.plugin"))
  implementation(groovy.util.Eval.x(project, "x.dep.gradleJapiCmpPlugin"))
  implementation(groovy.util.Eval.x(project, "x.dep.kotlin.plugin"))
  implementation(groovy.util.Eval.x(project, "x.dep.sqldelight.plugin"))
  implementation(groovy.util.Eval.x(project, "x.dep.gradlePublishPlugin"))
  // this plugin is added to the classpath but never applied, it is only used for the closeAndRelease code
  implementation(groovy.util.Eval.x(project, "x.dep.vanniktechPlugin"))
}