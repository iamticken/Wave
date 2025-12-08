plugins {
  id("wave-sample-app")
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "org.wave.qrtest"

  defaultConfig {
    applicationId = "org.wave.qrtest"
  }
}

dependencies {
  implementation(project(":qr"))

  implementation(libs.google.zxing.android.integration)
  implementation(libs.google.zxing.core)
}
