@file:Suppress("UnstableApiUsage")

import com.android.build.api.dsl.ManagedVirtualDevice
import org.gradle.api.JavaVersion
import org.gradle.kotlin.dsl.extra

val benchmarkLibs = the<org.gradle.accessors.dm.LibrariesForBenchmarkLibs>()

val waveBuildToolsVersion: String by rootProject.extra
val waveCompileSdkVersion: String by rootProject.extra
val waveTargetSdkVersion: Int by rootProject.extra
val waveMinSdkVersion: Int by rootProject.extra
val waveJavaVersion: JavaVersion by rootProject.extra
val waveKotlinJvmTarget: String by rootProject.extra

plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.wave.benchmark"
    compileSdkVersion = waveCompileSdkVersion

    compileOptions {
        sourceCompatibility = waveJavaVersion
        targetCompatibility = waveJavaVersion
    }

    kotlinOptions {
        jvmTarget = waveKotlinJvmTarget
    }

    defaultConfig {
        minSdk = 23
        targetSdk = waveTargetSdkVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        missingDimensionStrategy("environment", "prod")
        missingDimensionStrategy("distribution", "play")
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks += listOf("perf", "debug")
        }
    }

    targetProjectPath = ":Wave-Android"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    testOptions {
        managedDevices {
            devices {
                create("api31", ManagedVirtualDevice::class) {
                    device = "Pixel 6"
                    apiLevel = 31
                    systemImageSource = "aosp"
                    require64Bit = false
                }
            }
        }
    }

}

dependencies {
    implementation(benchmarkLibs.androidx.test.ext.junit)
    implementation(benchmarkLibs.espresso.core)
    implementation(benchmarkLibs.uiautomator)
    implementation(benchmarkLibs.androidx.benchmark.macro)
}

androidComponents {
    beforeVariants(selector().all()) {
        if (it.buildType != "benchmark") {
            it.enable = false
        }
    }
}
