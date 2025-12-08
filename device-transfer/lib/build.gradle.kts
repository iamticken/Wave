plugins {
  id("wave-library")
}

android {
  namespace = "org.wave.devicetransfer"
}

dependencies {
  implementation(project(":core-util"))
  implementation(libs.libwave.android)
  api(libs.greenrobot.eventbus)

  testImplementation(testLibs.robolectric.robolectric) {
    exclude(group = "com.google.protobuf", module = "protobuf-java")
  }
  testImplementation(testFixtures(project(":libwave-service")))
}
