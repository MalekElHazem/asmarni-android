plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.testr"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.testr"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
    configurations.all {
        exclude(group = "com.intellij", module = "annotations")
        resolutionStrategy {
            force("org.tensorflow:tensorflow-lite:latest.release")
            force("org.tensorflow:tensorflow-lite-select-tf-ops:latest.release")

        }
    }
    packagingOptions {
        // Exclude duplicate files
        exclude("META-INF/gradle/incremental.annotation.processors")
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.core.ktx)
    implementation(libs.room.compiler)
    //implementation(libs.litert)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    val camerax_version = "1.3.0"
    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")
    implementation("androidx.camera:camera-extensions:${camerax_version}")


    implementation("com.google.mediapipe:tasks-vision:latest.release")

    // TensorFlow Lite core library
    implementation("org.tensorflow:tensorflow-lite:latest.release")
    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:latest.release")

    implementation("org.jetbrains:annotations:13.0")



}