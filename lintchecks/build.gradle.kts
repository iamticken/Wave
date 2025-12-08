val waveJavaVersion: JavaVersion by rootProject.extra
val waveKotlinJvmTarget: String by rootProject.extra

plugins {
  id("java-library")
  id("org.jetbrains.kotlin.jvm")
}

java {
  sourceCompatibility = waveJavaVersion
  targetCompatibility = waveJavaVersion
}

kotlin {
  jvmToolchain {
    languageVersion = JavaLanguageVersion.of(waveKotlinJvmTarget)
  }
}

dependencies {
  compileOnly(lintLibs.lint.api)
  compileOnly(lintLibs.lint.checks)

  testImplementation(lintLibs.lint.tests)
  testImplementation(lintLibs.lint.api)
  testImplementation(testLibs.junit.junit)
}

tasks.jar {
  manifest {
    attributes(
      "Lint-Registry-v2" to "org.wave.lint.Registry"
    )
  }
}
