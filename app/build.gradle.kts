import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ── Signing config ────────────────────────────────────────────────────────────
// CI writes keystore.properties in the repo root before calling assembleRelease.
// Local developer builds skip signing (debug key is used automatically).
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(keystorePropsFile.inputStream())
}

// ── Version — driven by CI environment variables ──────────────────────────────
// BUILD_NUMBER  : set to github.run_number in CI  (monotonically increasing int)
// VERSION_NAME  : set to tag name or "1.0.<build>" in CI
// Local builds fall back to (1, "1.0-dev") so gradle always compiles.
val ciBuildNumber  = (System.getenv("BUILD_NUMBER")  ?: "1").toInt()
val ciVersionName  = System.getenv("VERSION_NAME")   ?: "1.0-dev"

android {
    namespace  = "com.poodlesoft.trafficlightdetector"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.poodlesoft.trafficlightdetector"
        minSdk        = 24
        targetSdk     = 34
        versionCode   = ciBuildNumber
        versionName   = ciVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Only include native libs for real phone ABIs.
        // Drops x86 / x86_64 (emulator-only) — saves ~30 MB from MediaPipe .so files.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile     = file(keystoreProps["storeFile"]  as String)
                storePassword = keystoreProps["storePassword"]   as String
                keyAlias      = keystoreProps["keyAlias"]        as String
                keyPassword   = keystoreProps["keyPassword"]     as String
            }
        }
    }

    buildTypes {
        release {
            // Minification disabled: MediaPipe resolves app classes by name via
            // JNI at runtime — R8 renaming them causes a crash even with keep rules.
            // The ABI filter already saves ~30 MB; re-enable minification only after
            // thorough device testing with a full mapping.txt review.
            isMinifyEnabled   = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseCfg = signingConfigs.findByName("release")
            if (releaseCfg != null) signingConfig = releaseCfg
        }
        debug {
            // No applicationIdSuffix — keeps the same package ID as release
            // so the app can be replaced in-place during testing.
            versionNameSuffix = "-debug"
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
        viewBinding  = true
        buildConfig  = true   // exposes BuildConfig.VERSION_NAME etc.
    }

    androidResources {
        noCompress += "tflite"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // CameraX
    val cameraxVersion = "1.3.2"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // MediaPipe Tasks Vision — EfficientDet-Lite0 model format
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
