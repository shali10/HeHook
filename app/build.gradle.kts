plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "top.hehook.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "top.hehook.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 100
        versionName = "1.0.0"

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    // AndroidX & UI
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // Modern LibXposed API (API 101) - 仅编译期引入，运行时由 LSPosed 提供
    compileOnly("io.github.libxposed:api:101.0.0")

    // DexKit C++ 模式匹配引擎 (全版本通杀核心)
    implementation("org.luckypray:dexkit:2.0.2")
}
