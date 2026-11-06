import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ---------------------------------------------------------------- 版本单源
val versionProps = Properties().apply {
    val f = rootProject.file("version.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val appVersionName: String = versionProps.getProperty("versionName") ?: "0.0.0"
val appVersionCode: Int = (versionProps.getProperty("versionCode") ?: "1").toInt()

/**
 * 校验 module.prop 与 version.properties 一致。
 * 版本号以前散在三处（gradle / module.prop / CI artifact 名），漏改一处就是版本错乱，
 * 现在不一致直接让构建失败。
 */
val verifyModuleProp by tasks.registering {
    val propFile = rootProject.file("app/src/main/resources/META-INF/xposed/module.prop")
    val expectedName = "v$appVersionName"
    val expectedCode = appVersionCode.toString()
    doLast {
        val text = propFile.readText()
        val nameLine = Regex("^version=(.+)$", RegexOption.MULTILINE).find(text)?.groupValues?.get(1)?.trim()
        val codeLine = Regex("^versionCode=(.+)$", RegexOption.MULTILINE).find(text)?.groupValues?.get(1)?.trim()
        require(nameLine == expectedName) {
            "module.prop 的 version=$nameLine 与 version.properties 的 $expectedName 不一致"
        }
        require(codeLine == expectedCode) {
            "module.prop 的 versionCode=$codeLine 与 version.properties 的 $expectedCode 不一致"
        }
    }
}

// ---------------------------------------------------------------- 签名
// 密钥不再进仓库：本地放 app/hehook-release.jks（已 gitignore），CI 由 secret 还原。
val keystoreOverride: String? = System.getenv("HEHOOK_KEYSTORE")
val keystoreFile: File = if (keystoreOverride != null) {
    File(keystoreOverride)
} else {
    rootProject.file("app/hehook-release.jks")
}
val keystoreReady = keystoreFile.exists()

android {
    namespace = "top.hehook.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "top.hehook.app"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
        }
    }

    if (keystoreReady) {
        signingConfigs {
            create("release") {
                storeFile = keystoreFile
                storePassword = System.getenv("HEHOOK_STORE_PASSWORD") ?: ""
                keyAlias = System.getenv("HEHOOK_KEY_ALIAS") ?: "hehook"
                keyPassword = System.getenv("HEHOOK_KEY_PASSWORD") ?: ""
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystoreReady) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        buildConfig = true
    }
}

tasks.named("preBuild").configure { dependsOn(verifyModuleProp) }

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
