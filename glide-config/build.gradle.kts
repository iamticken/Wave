plugins {
  id("wave-library")
  id("com.google.devtools.ksp")
}

android {
  namespace = "org.wave.glide"
}

dependencies {
  implementation(libs.glide.glide)
  ksp(libs.glide.ksp)
}
