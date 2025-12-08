plugins {
  alias(libs.plugins.jetbrains.kotlin.jvm)
  id("java-library")
  alias(libs.plugins.ktlint)
}

val waveJavaVersion: JavaVersion by rootProject.extra
val waveKotlinJvmTarget: String by rootProject.extra

java {
  sourceCompatibility = waveJavaVersion
  targetCompatibility = waveJavaVersion
}

kotlin {
  jvmToolchain {
    languageVersion = JavaLanguageVersion.of(waveKotlinJvmTarget)
  }
}

// NOTE: For now, in order to run ktlint on this project, you have to manually run ./gradlew :build-logic:tools:ktlintFormat
//       Gotta figure out how to get it auto-included in the normal ./gradlew ktlintFormat
ktlint {
  version.set("1.2.1")
}

dependencies {
  implementation(gradleApi())

  implementation(libs.dnsjava)
  testImplementation(testLibs.junit.junit)
  testImplementation(testLibs.mockk)
}
