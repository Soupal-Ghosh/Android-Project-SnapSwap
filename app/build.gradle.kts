plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    namespace = "com.example.snapy"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.snapy"
        minSdk = 24
        targetSdk = 34
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

// Force a single stable Guava version across ALL dependencies
configurations.all {
    resolutionStrategy {
        // Force the same stable Guava version everywhere
        force 'com.google.guava:guava:31.1-android'

        // Replace any listenablefuture dependencies with the full guava library
        eachDependency { details ->
            if (details.requested.group == 'com.google.guava') {
                if (details.requested.name == 'listenablefuture') {
                    // Replace standalone listenablefuture with full guava
                    details.useTarget group: 'com.google.guava', name: 'guava', version: '31.1-android'
                    details.because 'Use consistent stable Guava version with ListenableFuture included'
                } else if (details.requested.name == 'guava' && details.requested.version != '31.1-android') {
                    // Force all guava versions to use the same stable version
                    details.useVersion '31.1-android'
                    details.because 'Enforce consistent stable Guava version across all dependencies'
                }
            }
        }
    }
}

dependencies {
    // Explicitly declare the stable Guava version we want to use
    implementation("com.google.guava:guava:31.1-android")

    //  Cloudinary
    implementation("com.cloudinary:kotlin-url-gen:1.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20230227")

    //  Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    //  AndroidX Core & UI Components
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.5")

    //  UI Libraries
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("com.airbnb.android:lottie:5.2.0")
    implementation("androidx.activity:activity:1.8.2")
    implementation("androidx.activity:activity-ktx:1.8.2")

    //  Glide for Image Loading
    implementation("com.github.bumptech.glide:glide:4.12.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.12.0")

    //  Unit Testing
    testImplementation("junit:junit:4.13.2")

    //  Android Instrumentation Testing
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}