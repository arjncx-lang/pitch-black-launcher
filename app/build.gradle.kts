plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.lightest.launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lightest.launcher"
        minSdk = 24
        targetSdk = 37          // Upgraded: unlocks latest ART optimizations
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Use the debug keystore to sign the release build so it can be
    // installed directly on your device with full R8 optimizations active.
    signingConfigs {
        create("releaseWithDebugKey") {
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // R8 full mode: dead code elimination, aggressive inlining, class merging
            isMinifyEnabled = true
            // Strips unused resources (images, strings, layouts) from APK
            isShrinkResources = true
            proguardFiles(
                // proguard-android-optimize.txt enables R8's most aggressive optimizations
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign with debug key — fine for personal device use
            signingConfig = signingConfigs.getByName("releaseWithDebugKey")
        }
        debug {
            // Keep debug fast — no shrinking/obfuscation during development
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    // Pack metadata cleanly — reduces APK size slightly
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/**"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}