plugins {
  id("wave-sample-app")
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "org.wave.donations.app"

  defaultConfig {
    applicationId = "org.wave.donations.app"
  }
}

dependencies {
  implementation(project(":donations"))
  implementation(project(":core-util"))
}
