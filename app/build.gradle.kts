import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.devtools.ksp") version "1.9.25-1.0.20"
}

android {
    namespace = "com.example.lastdrop"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.lastdrop"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Load API key from local.properties (NOT committed to version control)
        val localProperties = project.rootProject.file("local.properties")
        if (localProperties.exists()) {
            val properties = Properties()
            properties.load(FileInputStream(localProperties))
            val apiKey = properties.getProperty("LASTDROP_API_KEY") ?: "ABC123"
            buildConfigField("String", "API_KEY", "\"$apiKey\"")
        } else {
            buildConfigField("String", "API_KEY", "\"ABC123\"")
        }
    }

    signingConfigs {
        create("release") {
            val localProperties = project.rootProject.file("local.properties")
            if (localProperties.exists()) {
                val properties = Properties()
                properties.load(FileInputStream(localProperties))
                
                storeFile = file("../lastdrop-release-key.jks")
                storePassword = properties.getProperty("KEYSTORE_PASSWORD")
                keyAlias = "lastdrop"
                keyPassword = properties.getProperty("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        buildConfig = true
    }
}

val room_version by extra { "2.6.1" }

dependencies {
    // From your version catalog (original)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(project(":godicesdklib"))

    // Extra standard UI libs (for AppCompat + Material XML widgets)
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // ✅ New: networking + async for ESP communication
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // ✅ QR Code Scanner (ZXing)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.3")

    // Room
    implementation("androidx.room:room-runtime:$room_version")
    ksp("androidx.room:room-compiler:$room_version")
    implementation("androidx.room:room-ktx:$room_version")

    // Tests (from your original file)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
