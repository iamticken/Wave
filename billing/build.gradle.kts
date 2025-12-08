plugins {
  id("wave-library")
}

android {
  namespace = "org.wave.billing"
}

dependencies {
  lintChecks(project(":lintchecks"))

  implementation(libs.android.billing)
  implementation(project(":core-util"))
}
