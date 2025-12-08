/*
 * Copyright 2023 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

plugins {
  id("wave-sample-app")
  alias(libs.plugins.compose.compiler)
}

val waveBuildToolsVersion: String by rootProject.extra
val waveCompileSdkVersion: String by rootProject.extra
val waveTargetSdkVersion: Int by rootProject.extra
val waveMinSdkVersion: Int by rootProject.extra
val waveJavaVersion: JavaVersion by rootProject.extra
val waveKotlinJvmTarget: String by rootProject.extra

android {
  namespace = "org.thoughtcrime.video.app"
  compileSdkVersion = waveCompileSdkVersion

  defaultConfig {
    applicationId = "org.thoughtcrime.video.app"
    minSdk = 23
    targetSdk = waveTargetSdkVersion
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables {
      useSupportLibrary = true
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = waveJavaVersion
    targetCompatibility = waveJavaVersion
  }
  kotlinOptions {
    jvmTarget = waveKotlinJvmTarget
  }
  buildFeatures {
    compose = true
  }
  composeOptions {
    kotlinCompilerExtensionVersion = "1.5.4"
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

dependencies {
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.material3)
  implementation(libs.bundles.media3)
  implementation(project(":video"))
  implementation(project(":core-util"))
  implementation("androidx.work:work-runtime-ktx:2.9.1")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
  implementation(libs.androidx.compose.ui.tooling.core)
  implementation(libs.androidx.compose.ui.test.manifest)
  androidTestImplementation(testLibs.junit.junit)
  androidTestImplementation(testLibs.androidx.test.runner)
  androidTestImplementation(testLibs.androidx.test.ext.junit.ktx)
}
