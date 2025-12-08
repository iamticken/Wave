plugins {
  id("wave-sample-app")
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "org.wave.debuglogsviewer.app"

  defaultConfig {
    applicationId = "org.wave.debuglogsviewer.app"
  }
}

dependencies {
  implementation(project(":debuglogs-viewer"))
  implementation(project(":core-util"))
}
