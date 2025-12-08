@file:Suppress("UnstableApiUsage")

plugins {
  id("com.android.library")
  id("androidx.benchmark")
  id("org.jetbrains.kotlin.android")
  id("ktlint")
}

val waveBuildToolsVersion: String by rootProject.extra
val waveCompileSdkVersion: String by rootProject.extra
val waveTargetSdkVersion: Int by rootProject.extra
val waveMinSdkVersion: Int by rootProject.extra
val waveJavaVersion: JavaVersion by rootProject.extra
val waveKotlinJvmTarget: String by rootProject.extra

android {
  namespace = "org.wave.microbenchmark"
  compileSdkVersion = waveCompileSdkVersion

  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = waveJavaVersion
    targetCompatibility = waveJavaVersion
  }

  kotlinOptions {
    jvmTarget = waveKotlinJvmTarget
  }

  defaultConfig {
    minSdk = waveMinSdkVersion
    testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
  }

  testBuildType = "release"
  buildTypes {
    debug {
      // Since isDebuggable can't be modified by gradle for library modules,
      // it must be done in a manifest - see src/androidTest/AndroidManifest.xml
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "benchmark-proguard-rules.pro")
    }
    release {
      isDefault = true
    }
  }
}

dependencies {
  coreLibraryDesugaring(libs.android.tools.desugar)
  lintChecks(project(":lintchecks"))

  implementation(project(":core-util"))
  implementation(project(":core-models"))

  // Base dependencies
  androidTestImplementation(testLibs.junit.junit)
  androidTestImplementation(benchmarkLibs.androidx.test.ext.junit)
  androidTestImplementation(benchmarkLibs.androidx.benchmark.micro)

  // Dependencies of modules being tested
  androidTestImplementation(project(":libwave-service"))
  androidTestImplementation(libs.libwave.android)
}
