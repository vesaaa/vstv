import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

/** CI/发布打标签时传入：./gradlew -PreleaseVersion=0.0.5 */
val releaseVersion = (project.findProperty("releaseVersion") as String?)?.trim().orEmpty()

/** 可选覆盖：-PversionCode=123；否则由 versionName 的 x.y.z 推导，保证侧载覆盖安装版本递增 */
fun semverToVersionCode(versionName: String): Int {
    val core = versionName.trim().removePrefix("v").substringBefore("-").substringBefore("+")
    val parts = core.split(".").mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return 1
    return parts[0].coerceIn(0, 999) * 1_000_000 +
        parts[1].coerceIn(0, 999) * 1_000 +
        parts[2].coerceIn(0, 999)
}

val defaultVersionName = "0.0.10"
val resolvedVersionName = releaseVersion.ifEmpty { defaultVersionName }
val resolvedVersionCode =
    (project.findProperty("versionCode") as String?)?.toIntOrNull()
        ?: semverToVersionCode(resolvedVersionName)

/** GitHub Actions 等未提供 jks 时：-PciUseDebugSigning=true，release 使用 debug 密钥（仅测试包） */
val ciUseDebugSigning = project.hasProperty("ciUseDebugSigning")

android {
    namespace = "top.yogiczy.mytv"
    compileSdk = 34

    defaultConfig {
        applicationId = "top.yogiczy.mytv"
        minSdk = 21
        targetSdk = 34
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86_64"))
        }
    }

    flavorDimensions += "dist"
    productFlavors {
        create("original") {
            dimension = "dist"
            buildConfigField("boolean", "USE_X5", "false")
            ndk {
                abiFilters.clear()
                abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86_64"))
            }
        }
        create("disguised") {
            dimension = "dist"
            applicationId = "com.chinablue.tv"
            buildConfigField("boolean", "USE_X5", "false")
            resValue("string", "app_name", "Z视介")
            ndk {
                abiFilters.clear()
                abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86_64"))
            }
        }
        create("originalX5Arm64") {
            dimension = "dist"
            buildConfigField("boolean", "USE_X5", "true")
            ndk {
                abiFilters.clear()
                abiFilters.add("arm64-v8a")
            }
        }
        create("originalX5Armeabi") {
            dimension = "dist"
            buildConfigField("boolean", "USE_X5", "true")
            ndk {
                abiFilters.clear()
                abiFilters.add("armeabi-v7a")
            }
        }
    }

    sourceSets {
        getByName("originalX5Arm64") {
            java.srcDir("src/x5/java")
        }
        getByName("originalX5Armeabi") {
            java.srcDir("src/x5/java")
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
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (!ciUseDebugSigning) {
            create("release") {
                storeFile =
                    file(System.getenv("KEYSTORE") ?: keystoreProperties["storeFile"] ?: "keystore.jks")
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                    ?: keystoreProperties.getProperty("storePassword")
                keyAlias = System.getenv("KEY_ALIAS") ?: keystoreProperties.getProperty("keyAlias")
                keyPassword =
                    System.getenv("KEY_PASSWORD") ?: keystoreProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = if (ciUseDebugSigning) {
                signingConfigs.getByName("debug")
            } else {
                signingConfigs.getByName("release")
            }
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.androidx.material.icons.extended)

    // TV Compose
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)

    // 播放器（HLS / DASH / SmoothStreaming / RTSP）。官方未在 Maven 发布 FFmpeg AAR，软解依赖设备 MediaCodec。
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.smoothstreaming)
    implementation(libs.androidx.media3.exoplayer.rtsp)

    // 序列化
    implementation(libs.kotlinx.serialization)

    // 网络请求
    implementation(libs.okhttp)
    implementation(libs.androidasync)

    // 二维码
    implementation(libs.qrose)

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    val tbsSdk = "com.tencent.tbs:tbssdk:44286"
    add("originalX5Arm64Implementation", tbsSdk)
    add("originalX5ArmeabiImplementation", tbsSdk)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}