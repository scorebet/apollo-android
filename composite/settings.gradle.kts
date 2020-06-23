rootProject.name = "apollo-composite"

// Samples
include(":java-sample")
project(":java-sample").projectDir = file("../samples/java-sample")
include(":kotlin-sample")
project(":kotlin-sample").projectDir = file("../samples/kotlin-sample")
include(":multiplatform")
project(":multiplatform").projectDir = file("../samples/multiplatform")
include(":multiplatform:kmp-android-app")
project(":multiplatform:kmp-android-app").projectDir = file("../samples/multiplatform/kmp-android-app")
include(":multiplatform:kmp-lib-sample")
project(":multiplatform:kmp-lib-sample").projectDir = file("../samples/multiplatform/kmp-lib-sample")

// Integration Tests
include(":apollo-integration")
project(":apollo-integration").projectDir = file("../apollo-integration")

includeBuild("../")
