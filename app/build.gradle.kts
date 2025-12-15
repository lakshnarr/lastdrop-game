plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose) // keep compose plugin so ui.theme compiles
    id("com.google.devtools.ksp") version "2.0.21-1.0.28"
}

android {
    namespace = "earth.lastdrop.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "earth.lastdrop.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 6
        versionName = "1.0.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Load API key from local.properties
        val properties = org.jetbrains.kotlin.konan.properties.Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            properties.load(localPropertiesFile.inputStream())
        }
        buildConfigField("String", "API_KEY", "\"${properties.getProperty("LASTDROP_API_KEY", "ABC123")}\"")
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("lastdrop-release-key.jks")
            storePassword = "Lastdrop1!app"
            keyAlias = "lastdrop"
            keyPassword = "Lastdrop1!app"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    // We keep compose = true (for the theme files),
    // but our MainActivity uses XML with setContentView, which is fine.
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

val room_version by extra { "2.6.1" }

dependencies {
    // From your version catalog (original)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(project(":godicesdklib"))

    // Extra standard UI libs (for AppCompat + Material XML widgets)
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // ✅ New: networking + async for ESP communication
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Room
    implementation("androidx.room:room-runtime:$room_version")
    ksp("androidx.room:room-compiler:$room_version")
    implementation("androidx.room:room-ktx:$room_version")

    // QR Code generation and scanning
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Lottie animations for character emotes
    implementation("com.airbnb.android:lottie:6.2.0")

    // Tests (from your original file)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Auto-install to connected device after every debug build
afterEvaluate {
    tasks.named("assembleDebug").configure {
        finalizedBy("installDebug")
    }
}
