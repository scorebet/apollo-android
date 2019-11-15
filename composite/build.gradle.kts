buildscript {
  project.apply {
    from(rootProject.file("../gradle/dependencies.gradle"))
  }

  repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
  }

  dependencies {
    classpath(groovy.util.Eval.x(project, "x.dep.android.plugin"))
    classpath(groovy.util.Eval.x(project, "x.dep.kotlin.plugin"))
    classpath("com.apollographql.apollo:apollo-gradle-plugin-incubating")
  }
}

subprojects {
  repositories {
    google()
    mavenCentral()
    jcenter()
  }
}
