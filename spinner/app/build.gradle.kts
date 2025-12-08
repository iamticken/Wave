plugins {
  id("wave-sample-app")
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "org.wave.spinnertest"

  defaultConfig {
    applicationId = "org.wave.spinnertest"
  }
}

dependencies {
  implementation(project(":spinner"))

  implementation(libs.androidx.sqlite)
  implementation(libs.wave.android.database.sqlcipher)
}
