plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

val ytdlpVersion: String = providers.gradleProperty("ytdlpVersion").get()
val ffmpegkitVersion: String = providers.gradleProperty("ffmpegkitVersion").get()

android {
    namespace = "com.chaman.downloader"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.chaman.downloader"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.2.0-phase0b"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

chaquopy {
    defaultConfig {
        version = "3.11"
        buildPython("python3")
        pip {
            install("yt-dlp==$ytdlpVersion")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Community-maintained FFmpegKit fork (LGPL). Phase 0 me test ho raha hai.
    implementation("dev.ffmpegkit-maintained:ffmpeg:$ffmpegkitVersion")
}
