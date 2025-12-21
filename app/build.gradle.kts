plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Note: Avoid overriding buildDir on Windows. It can sometimes make file-lock issues worse
// (e.g., R.jar staying locked in the alternate folder). Keep default Gradle build dir.

android {
    namespace = "com.kiranaflow.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kiranaflow.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Backend wiring (Milestone D+): configured via gradle.properties/local.properties.
        // Keep secrets OUT of VCS. Defaults are blank (no real remote).
        val backendBaseUrl = (project.findProperty("KIRANAFLOW_BACKEND_BASE_URL") as String?) ?: ""
        val backendApiKey = (project.findProperty("KIRANAFLOW_BACKEND_API_KEY") as String?) ?: ""
        buildConfigField("String", "BACKEND_BASE_URL", "\"$backendBaseUrl\"")
        buildConfigField("String", "BACKEND_API_KEY", "\"$backendApiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // Address 16KB alignment issue for native libraries from MLKit
        // For Android 15+ 16KB page size compatibility
        jniLibs {
            useLegacyPackaging = true
            // This allows the app to install on 16KB devices by using legacy packaging
            // which doesn't require perfect alignment (workaround until ML Kit libraries are updated)
        }
    }
}

// Suppress the Java version warnings
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        freeCompilerArgs += "-Xsuppress-version-warnings"
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Explicitly add animation dependency to resolve 'tween' issues
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended")
    // Add LiveData support for Compose
    implementation("androidx.compose.runtime:runtime-livedata")

    // Navigation & ViewModel
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Networking (real backend wiring)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // DataStore (Shop Settings persistence)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // CameraX
    val cameraXVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")

    // ML Kit - Using legacy packaging workaround for 16KB page size compatibility
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    // OCR (vendor bill scanning)
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.0") // Hindi

    // Image loading (for product photos)
    implementation("io.coil-kt:coil-compose:2.5.0")

    // QR generation (UPI dynamic QR)
    implementation("com.google.zxing:core:3.5.3")

    // Biometric auth (privacy overlay unlock)
    implementation("androidx.biometric:biometric:1.1.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
