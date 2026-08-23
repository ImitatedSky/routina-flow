import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// ---- Release 簽章的來源（keystore/密碼絕不進版控）----
// 優先讀專案根目錄的 keystore.properties（已被 .gitignore 排除），其次讀環境變數
// （CI 用 KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD）。
// 四者不齊全 → 不建立正式簽章，release 會 fallback 沿用 debug 簽章，
// 讓本機與現行「無 secret」的 CI 都能照樣 build 綠燈。
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun releaseSigningValue(propKey: String, envKey: String): String? =
    keystoreProperties.getProperty(propKey) ?: System.getenv(envKey)

val releaseStoreFile = releaseSigningValue("storeFile", "KEYSTORE_FILE")
val releaseStorePassword = releaseSigningValue("storePassword", "KEYSTORE_PASSWORD")
val releaseKeyAlias = releaseSigningValue("keyAlias", "KEY_ALIAS")
val releaseKeyPassword = releaseSigningValue("keyPassword", "KEY_PASSWORD")
val hasReleaseSigning = !releaseStoreFile.isNullOrBlank() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.routina.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.routina.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 16
        versionName = "0.16.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        // 只有在提供了完整的正式簽章資訊時才建立 release signingConfig；
        // 否則不建立，buildType 會 fallback 到 debug 簽章（見下）。
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 有正式 keystore（keystore.properties 或 CI env）時用 release 簽章；
            // 否則沿用 debug 簽章——個人側載用途，零密鑰管理負擔（見 SECURITY.md）。
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            isMinifyEnabled = false
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
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.version",
                "/META-INF/*.kotlin_module",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json"
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // 區域觸發：系統級地理圍欄 + 目前位置
    implementation("com.google.android.gms:play-services-location:21.3.0")
    // 地圖選點：OpenStreetMap 圖資，免 API key
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // 拍照 / 連拍：CameraX 無預覽 ImageCapture（比 Camera2 大幅降低複雜度）
    val cameraX = "1.4.2"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // 動作積木拖曳排序：非 lazy 的 ReorderableColumn，與編輯畫面的可捲動 Column 相容
    implementation("sh.calvin.reorderable:reorderable:2.5.1")
}
