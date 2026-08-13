import org.jetbrains.kotlin.gradle.dsl.JvmTarget

import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.squelch.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.squelch.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Release: load from local.properties. Default to the debug
        // signing config so `assembleRelease` still builds if you
        // haven't run tools/setup-release-keystore.sh yet (the resulting
        // APK won't be Play-uploadable; check the setup script).
        val keystoreProps = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) load(FileInputStream(f))
        }
        val keystoreFile = keystoreProps.getProperty(
            "squelch.keystore.file", "keystore/squelch.jks"
        )
        val storePass = keystoreProps.getProperty(
            "squelch.keystore.storepass", ""
        )
        val keyPass = keystoreProps.getProperty(
            "squelch.keystore.keypass", ""
        )
        val alias = keystoreProps.getProperty(
            "squelch.keystore.alias", "squelch"
        )

        create("release") {
            if (rootProject.file(keystoreFile).exists() && storePass.isNotEmpty()) {
                storeFile = rootProject.file(keystoreFile)
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            // Same signing identity as release so the OAuth client only
            // needs ONE SHA-1 registered. Swap back to the default debug
            // keystore when you create a separate OAuth client for it.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,DEPENDENCIES,LICENSE,NOTICE}"
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-service:2.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.9.0")

    // Auth: Google Sign-In. Drive is called via raw REST using a Bearer token
// obtained through GoogleAuthUtil, so the v3 client library is not needed.
    implementation("com.google.android.gms:play-services-auth:21.0.0")

    // Google Nearby Connections for the offline mesh (spec section 3.3).
    implementation("com.google.android.gms:play-services-nearby:19.3.0")

    // Encrypted local DB. Room gives us the ergonomics; SQLCipher backs the file.
    implementation("net.zetetic:sqlcipher-android:4.5.4@aar")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    // Cryptography: BouncyCastle for the Argon2id KDF used to wrap the vault.
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")

    // JSON (vault payload, contact exchange). org.json is part of the Android
    // SDK at compile time and avoids pulling in kotlinx-serialization plugin.

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    // WebSocket client for the online relay transport (M-online).
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")
}
