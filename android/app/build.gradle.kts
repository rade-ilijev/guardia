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
    compileSdk = 35

    defaultConfig {
        applicationId = "com.guardia.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Picovoice AccessKey (set picovoice.accessKey in local.properties). Empty disables voice.
        buildConfigField("String", "PICOVOICE_ACCESS_KEY", "\"${localProps.getProperty("picovoice.accessKey", "")}\"")
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
    // Store native libraries uncompressed and page-aligned so they load on
    // Android 15+ devices that use a 16 KB memory page size.
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            // JavaMail / activation / jakarta.inject ship duplicate license metadata.
            excludes += setOf(
                "META-INF/NOTICE.md",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE",
                "META-INF/LICENSE",
                "META-INF/DEPENDENCIES",
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

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
