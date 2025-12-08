@file:Suppress("UnstableApiUsage")

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.accessors.dm.LibrariesForTestLibs
import org.gradle.api.JavaVersion
import org.gradle.kotlin.dsl.extra

val libs = the<LibrariesForLibs>()
val testLibs = the<LibrariesForTestLibs>()

val waveBuildToolsVersion: String by rootProject.extra
val waveCompileSdkVersion: String by rootProject.extra
val waveTargetSdkVersion: Int by rootProject.extra
val waveMinSdkVersion: Int by rootProject.extra
val waveJavaVersion: JavaVersion by rootProject.extra
val waveKotlinJvmTarget: String by rootProject.extra

plugins {
  // We cannot use the version catalog in the plugins block in convention plugins (it's not supported).
  // Instead, plugin versions are controlled through the dependencies block in the build.gradle.kts.
  id("com.android.library")
  id("kotlin-android")
  id("ktlint")
}

android {
  buildToolsVersion = waveBuildToolsVersion
  compileSdkVersion = waveCompileSdkVersion

  defaultConfig {
    minSdk = waveMinSdkVersion
  }

  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = waveJavaVersion
    targetCompatibility = waveJavaVersion
  }

  kotlinOptions {
    jvmTarget = waveKotlinJvmTarget
    suppressWarnings = true
  }

  lint {
    disable += "InvalidVectorPath"
  }
}

dependencies {
  lintChecks(project(":lintchecks"))

  coreLibraryDesugaring(libs.android.tools.desugar)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.annotation)
  implementation(libs.androidx.appcompat)
  implementation(libs.rxjava3.rxandroid)
  implementation(libs.rxjava3.rxjava)
  implementation(libs.rxjava3.rxkotlin)
  implementation(libs.kotlin.stdlib.jdk8)

  ktlintRuleset(libs.ktlint.twitter.compose)

  testImplementation(testLibs.junit.junit)
  testImplementation(testLibs.robolectric.robolectric)
  testImplementation(testLibs.androidx.test.core)
  testImplementation(testLibs.androidx.test.core.ktx)
}
