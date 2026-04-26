plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.trafficlightdetector"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.trafficlightdetector"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Release signing is injected via -P flags from the GitHub Actions workflow.
        // When the KEYSTORE_BASE64 secret is set the workflow passes these properties;
        // otherwise the build falls back to the debug key automatically.
        val storeFile   = findProperty("android.injected.signing.store.file")   as String?
        val storePass   = findProperty("android.injected.signing.store.password") as String?
        val keyAlias    = findProperty("android.injected.signing.key.alias")    as String?
        val keyPass     = findProperty("android.injected.signing.key.password") as String?

        if (storeFile != null) {
            create("release") {
                this.storeFile     = file(storeFile)
                this.storePassword = storePass
                this.keyAlias      = keyAlias
                this.keyPassword   = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use the release signing config only when it was configured above
            val releaseCfg = signingConfigs.findByName("release")
            if (releaseCfg != null) signingConfig = releaseCfg
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

    // Prevent compression of .tflite model files
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

    // TensorFlow Lite - Task Vision API (handles model + labels via metadata)
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4")
    // GPU acceleration (optional, falls back to CPU)
    implementation("org.tensorflow:tensorflow-lite-gpu-delegate-plugin:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.9.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
