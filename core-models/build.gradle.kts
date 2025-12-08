/*
 * Copyright 2025 Wave Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

val waveJavaVersion: JavaVersion by rootProject.extra
val waveKotlinJvmTarget: String by rootProject.extra

plugins {
  id("java-library")
  id("org.jetbrains.kotlin.jvm")
  id("ktlint")
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
  implementation(libs.libwave.client)
  implementation(libs.square.okio)
  implementation(project(":core-util-jvm"))
}
