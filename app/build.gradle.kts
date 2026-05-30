plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.dhruvtej.payoff"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dhruvtej.payoff"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "d-1.10"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures{
        buildConfig = true
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation("androidx.lifecycle:lifecycle-viewmodel-android:2.10.0")
    implementation(libs.constraintlayout)
    implementation(libs.camera.view)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("androidx.camera:camera-core:1.6.1")
    implementation("androidx.camera:camera-camera2:1.6.1")
    implementation("androidx.camera:camera-lifecycle:1.6.1")
    implementation("androidx.camera:camera-view:1.6.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("androidx.biometric:biometric:1.1.0")
}