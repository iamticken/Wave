@file:Suppress("UnstableApiUsage")

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.accessors.dm.LibrariesForTestLibs
import org.gradle.api.JavaVersion
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.the

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
  id("com.android.application")
  id("kotlin-android")
  id("ktlint")
}

android {
  buildToolsVersion = waveBuildToolsVersion
  compileSdkVersion = waveCompileSdkVersion

  defaultConfig {
    versionCode = 1
    versionName = "1.0"

    minSdk = waveMinSdkVersion
    targetSdk = waveTargetSdkVersion
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

  buildFeatures {
    buildConfig = true
    compose = true
  }

  composeOptions {
    kotlinCompilerExtensionVersion = "1.5.4"
  }
}

dependencies {
  coreLibraryDesugaring(libs.android.tools.desugar)

  implementation(project(":core-util"))

  coreLibraryDesugaring(libs.android.tools.desugar)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.annotation)
  implementation(libs.androidx.appcompat)
  implementation(libs.rxjava3.rxandroid)
  implementation(libs.rxjava3.rxjava)
  implementation(libs.rxjava3.rxkotlin)
  implementation(libs.material.material)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.kotlin.stdlib.jdk8)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.material3)

  ktlintRuleset(libs.ktlint.twitter.compose)

  testImplementation(testLibs.junit.junit)
  testImplementation(testLibs.robolectric.robolectric)
  testImplementation(testLibs.androidx.test.core)
  testImplementation(testLibs.androidx.test.core.ktx)
}
