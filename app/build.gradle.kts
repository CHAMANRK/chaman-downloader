plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
    id("com.google.devtools.ksp")
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
        versionCode = 36
        versionName = "0.4.30-queue"

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
    // Community-maintained FFmpegKit fork, audio package (LGPL, lame included). Same com.arthenica.ffmpegkit API.
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-audio:$ffmpegkitVersion")
    // ffmpeg-kit-audio ka POM ye helper saath nahi laata (v0.4.15 device: NoClassDefFoundError com.arthenica.smartexception.java.Exceptions)
    implementation("com.arthenica:smart-exception-java:0.2.1")

    // Queue Room (Step U). Versions meri yaad se, web se verify nahi: CI resolve hona hi asli check.
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
}
