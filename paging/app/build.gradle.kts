plugins {
  id("wave-sample-app")
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "org.wave.pagingtest"

  defaultConfig {
    applicationId = "org.wave.pagingtest"
  }
}

dependencies {
  implementation(project(":paging"))
}
