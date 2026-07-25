plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.example.smsgateway"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.smsgateway"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("org.json:json:20230227")
    implementation("androidx.work:work-runtime-ktx:2.8.1")
}
