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
        minSdk = 29 // MediaStore.Downloads (scoped storage) ke liye Android 10+
        targetSdk = 34
        versionCode = 12
        versionName = "0.4.7-polish"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    // Permanent signing key: CI secrets se env vars aate hain. Na mile to debug key (fallback).
    val chamanKeystore = System.getenv("CHAMAN_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (chamanKeystore != null) {
            create("chaman") {
                storeFile = chamanKeystore
                storePassword = System.getenv("CHAMAN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CHAMAN_KEY_ALIAS") ?: "chaman"
                keyPassword = System.getenv("CHAMAN_KEYSTORE_PASSWORD") // PKCS12: key password = store password
            }
        }
    }
    buildTypes {
        getByName("debug") {
            signingConfigs.findByName("chaman")?.let { signingConfig = it }
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

    testImplementation("junit:junit:4.13.2")
}
