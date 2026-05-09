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

    val defaultVersionName = "2.0.20"
val resolvedVersionName = releaseVersion.ifEmpty { defaultVersionName }
val resolvedVersionCode =
    (project.findProperty("versionCode") as String?)?.toIntOrNull()
        ?: semverToVersionCode(resolvedVersionName)

/** GitHub Actions 等未提供 jks 时：-PciUseDebugSigning=true，release 使用 debug 密钥（仅测试包） */
val ciUseDebugSigning = project.hasProperty("ciUseDebugSigning")

android {
    namespace = "com.vesaa.mytv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vesaa.mytv"
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

    // dist：包名/应用名；abiPack：按架构拆包（保留 3 个变体）
    flavorDimensions += listOf("dist", "abiPack")
    productFlavors {
        create("original") {
            dimension = "dist"
        }
        create("disguised") {
            dimension = "dist"
            applicationId = "com.chinablue.tv"
            resValue("string", "app_name", "Z视介")
        }
        create("arm") {
            dimension = "abiPack"
            ndk {
                abiFilters.clear()
                abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
            }
        }
        create("x86") {
            dimension = "abiPack"
            ndk {
                abiFilters.clear()
                abiFilters.addAll(listOf("x86_64"))
            }
        }
    }
    variantFilter {
        val distFlavor = flavors.find { it.dimension == "dist" }?.name
        val abiFlavor = flavors.find { it.dimension == "abiPack" }?.name
        // 仅保留 3 个发布包：originalArm / originalX86 / disguisedArm
        if (distFlavor == "disguised" && abiFlavor == "x86") {
            ignore = true
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
                val storeTypeEnv = System.getenv("KEYSTORE_TYPE")?.takeIf { it.isNotBlank() }
                    ?: keystoreProperties.getProperty("storeType")?.takeIf { it.isNotBlank() }
                if (storeTypeEnv != null) {
                    storeType = storeTypeEnv
                }
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

configurations.configureEach {
    // Jellyfin 的 media3-ffmpeg-decoder 已自带 libffmpegJNI.so。
    // 若依赖树里同时引入 AndroidX 的 ffmpeg decoder/底层 lib，会在 mergeNativeLibs 阶段报重复 so。
    exclude(group = "androidx.media3", module = "media3-decoder-ffmpeg")
    exclude(group = "androidx.media3", module = "lib-decoder-ffmpeg")
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
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

    // 播放器（HLS / DASH / SmoothStreaming / RTSP / RTMP）。
    // 默认集成 Jellyfin Media3 FFmpeg 扩展：硬解优先，硬解失败时软解兜底。
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.smoothstreaming)
    implementation(libs.androidx.media3.exoplayer.rtsp)
    implementation(libs.androidx.media3.datasource.rtmp)
    implementation(libs.jellyfin.media3.ffmpeg.decoder) {
        // mediax 当前包的 POM 里显式带了 androidx.media3 传递依赖（版本可能高于本工程）。
        // 这里排除传递依赖，统一使用本工程显式声明的 media3 版本（当前 1.8.0）。
        exclude(group = "androidx.media3", module = "media3-decoder")
        exclude(group = "androidx.media3", module = "media3-exoplayer")
    }
    // 序列化
    implementation(libs.kotlinx.serialization)

    // 网络请求
    implementation(libs.okhttp)
    implementation(libs.androidasync)

    // 二维码
    implementation(libs.qrose)

    // 频道台标（网络图片缓存）
    implementation(libs.coil.compose)

    // 后台定时拉取节目单
    implementation(libs.androidx.work.runtime.ktx)

    implementation(
        fileTree(
            mapOf(
                "dir" to "libs",
                "include" to listOf("*.aar"),
                // CI 恢复的私有包里可能包含历史 ffmpeg 扩展，需排除以避免与 Jellyfin 版本重复。
                "exclude" to listOf("lib-decoder-ffmpeg*.aar", "media3-decoder-ffmpeg*.aar"),
            ),
        ),
    )

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}


