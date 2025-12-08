plugins {
  id("wave-sample-app")
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "org.wave.devicetransfer.app"

  defaultConfig {
    applicationId = "org.wave.devicetransfer.app"

    ndk {
      abiFilters += setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
    }

    buildConfigField("String", "LIBSIGNAL_VERSION", "\"libwave ${libs.versions.libwave.client.get()}\"")
  }
}

dependencies {
  implementation(project(":device-transfer"))
}
