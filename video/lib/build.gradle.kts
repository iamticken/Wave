plugins {
  id("wave-library")
}

android {
  namespace = "org.wave.video"
}

dependencies {
  implementation(project(":core-util"))
  implementation(libs.libwave.android)
  implementation(libs.google.guava.android)

  implementation(libs.bundles.mp4parser) {
    exclude(group = "junit", module = "junit")
  }
}
