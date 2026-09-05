import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val signingProps = Properties().apply {
    val f = rootProject.file("signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.guardia.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.guardia.app"
        minSdk = 26
        // Google Play has required API 36 (Android 16) for all new submissions and updates
        // since 31 August 2026 — a build targeting 35 is rejected at upload.
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Picovoice AccessKey (set picovoice.accessKey in local.properties). Empty disables voice.
        buildConfigField("String", "PICOVOICE_ACCESS_KEY", "\"${localProps.getProperty("picovoice.accessKey", "")}\"")

        // SHA-256 of the release signing certificate, for on-device repackaging detection
        // (IntegrityGuard). Set signing.expectedSha256 in signing.properties once you have the
        // upload cert; empty means "not pinned" so debug/unconfigured builds never false-alarm.
        buildConfigField("String", "EXPECTED_SIGNING_SHA256", "\"${signingProps.getProperty("expectedSha256", "")}\"")
    }

    // Two distribution variants:
    //  - full: sideload build with every capability (SMS find-my-phone, background location,
    //          screenshot-based per-app check styles).
    //  - play: the Google Play build. Drops the most heavily-scrutinized capabilities (SMS,
    //          background location) and ships a minimal Accessibility config (no screen capture),
    //          so the first store submission carries the fewest restricted declarations.
    flavorDimensions += "distribution"
    productFlavors {
        create("full") {
            dimension = "distribution"
            applicationIdSuffix = ".full"
            versionNameSuffix = "-full"
            buildConfigField("boolean", "PLAY_BUILD", "false")
        }
        create("play") {
            dimension = "distribution"
            // Keeps the published applicationId `com.guardia.app`.
            buildConfigField("boolean", "PLAY_BUILD", "true")
        }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = signingProps.getProperty("storeFile") ?: return@create
            storeFile = rootProject.file(storeFilePath)
            storePassword = signingProps.getProperty("storePassword")
            keyAlias = signingProps.getProperty("keyAlias")
            keyPassword = signingProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isDebuggable = false
            // R8 code shrink + obfuscation (a security app shouldn't ship trivially decompilable),
            // plus unused-resource stripping. Keep rules live in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
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
        compose = true
        buildConfig = true
    }
    androidResources {
        noCompress += "tflite"
    }
    // Ship only the language, density and ABI resources each device actually needs.
    bundle {
        language { enableSplit = true }
        density { enableSplit = true }
        abi { enableSplit = true }
    }

    // Store native libraries uncompressed and page-aligned so they load on
    // Android 15+ devices that use a 16 KB memory page size.
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            // JavaMail / activation / jakarta.inject ship duplicate license metadata, and the
            // Kotlin/coroutines artifacts ship module metadata and debug probes that only the
            // compiler and the debugger read. (The `kotlin/` builtins are deliberately *not*
            // stripped — kotlin-reflect needs them, and nothing here is worth that risk.)
            // None of the entries below is used at runtime, so they are stripped:
            // less to unpack on install, and a smaller download.
            excludes += setOf(
                "META-INF/NOTICE.md",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE",
                "META-INF/LICENSE",
                "META-INF/DEPENDENCIES",
                "META-INF/*.version",
                "META-INF/*.kotlin_module",
                "DebugProbesKt.bin",
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Data / async
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.security.crypto)
    // NOTE: no WorkManager — guarding runs via a foreground service, and its startup
    // ContentProvider costs launch time, so we don't pull it in.

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Camera + on-device ML
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.face.detection)
    implementation(libs.litert)
    implementation(libs.litert.support)
    // 16 KB-aligned build of the native lib Compose pulls in transitively.
    implementation(libs.androidx.graphics.path)

    // Voice safeword
    implementation(libs.picovoice.porcupine)

    // Image loading
    implementation(libs.coil.compose)

    // Alerts & Recovery (email/SMS/find-my-phone)
    implementation(libs.play.services.location)
    implementation(libs.javamail.android)
    implementation(libs.javamail.activation)

    // Billing (subscription)
    implementation(libs.billing.ktx)

    // Baseline/cloud ART profile installation (startup + jank; profiles land via Play or a
    // future macrobenchmark module — see the perf notes in DESIGN.md).
    implementation(libs.androidx.profileinstaller)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
