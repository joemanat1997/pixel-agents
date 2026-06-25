plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.applocker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.applocker"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Keep only the languages we actually ship; strips ~80 unused locale
        // string tables that appcompat/material would otherwise bundle.
        resourceConfigurations += listOf("en", "th", "zh", "ja", "es", "nb")
    }

    buildTypes {
        release {
            // Shrink + obfuscate code and remove unused resources for a small APK.
            isMinifyEnabled = true
            isShrinkResources = true
            // Sign release with the debug key so a release APK can be built and
            // installed directly (Build Variant: release) without extra setup.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}

// Pin AndroidX to versions that compile against compileSdk 34. Without this,
// transitive resolution can drag in 2026-era releases (e.g. core 1.19.0,
// lifecycle-runtime-compose 2.11.0) that demand compileSdk 37.
configurations.all {
    resolutionStrategy {
        force(
            "androidx.core:core:1.13.1",
            "androidx.core:core-ktx:1.13.1",
            "androidx.appcompat:appcompat:1.7.0",
            "com.google.android.material:material:1.12.0",
            "androidx.activity:activity:1.8.2",
            "androidx.activity:activity-ktx:1.8.2",
            "androidx.lifecycle:lifecycle-runtime:2.7.0",
            "androidx.lifecycle:lifecycle-runtime-ktx:2.7.0",
            "androidx.lifecycle:lifecycle-common:2.7.0"
        )
    }
    // This project uses Views, not Compose; drop any Compose lifecycle artifacts
    // that would otherwise force a newer compileSdk.
    exclude(group = "androidx.lifecycle", module = "lifecycle-runtime-compose")
    exclude(group = "androidx.lifecycle", module = "lifecycle-runtime-compose-android")
}
