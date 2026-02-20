import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// local.properties から値を読み込む設定
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.ratolab.carrierbandanalyzer"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.ratolab.carrierbandanalyzer"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "1.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ★ AdMob IDの設定
        // マニフェスト用
        manifestPlaceholders["admobAppId"] = localProperties.getProperty("ADMOB_APP_ID") ?: ""
        // Kotlinコード(MainActivity)用
        val bannerId = localProperties.getProperty("ADMOB_BANNER_UNIT_ID") ?: ""
        buildConfigField("String", "BANNER_ID", "\"$bannerId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            ndk.debugSymbolLevel = "full"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // ★ buildConfigFieldを使うために必要
        buildConfig = true
    }
}

dependencies {
    // 基本セット
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("com.google.android.material:material:1.12.0")

    // Compose (UI構築の核心)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // アイコン
    implementation("androidx.compose.material:material-icons-extended:1.6.0")

    // AdMob SDK (最新版)
    implementation("com.google.android.gms:play-services-ads:24.9.0")

    // テスト系
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}