plugins {
  id("wave-library")
}

android {
  namespace = "org.wave.paging"
}

dependencies {
  implementation(project(":core-util"))
}
