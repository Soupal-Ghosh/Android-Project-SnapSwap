plugins {
    id("com.android.application") // Use the ID for the plugin without version catalog
    id("org.jetbrains.kotlin.android") // Use the ID for Kotlin plugin without version catalog
    id("kotlin-kapt")
}

android {
    namespace = "com.example.snapy"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.snapy"
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

dependencies {
    // ✅ AndroidX Core & UI Components
    implementation("com.google.android.material:material:1.9.0") // Direct version
    implementation("androidx.appcompat:appcompat:1.6.1") // Direct version
    implementation("androidx.constraintlayout:constraintlayout:2.1.4") // Direct version
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.5") // Direct version
    implementation("androidx.navigation:navigation-ui-ktx:2.7.5") // Direct version
    implementation("androidx.core:core-ktx:1.12.0") // Direct version

    // ✅ UI Libraries
    implementation("androidx.recyclerview:recyclerview:1.4.0") // Direct version
    implementation("androidx.viewpager2:viewpager2:1.1.0") // Direct version
    implementation("com.airbnb.android:lottie:5.2.0") // Direct version
    implementation("androidx.activity:activity:1.10.1") // Direct version

    // ✅ Glide for Image Loading
    implementation("com.github.bumptech.glide:glide:4.15.1") // Direct version for Glide
    kapt("com.github.bumptech.glide:compiler:4.15.1") // Direct version for Glide KAPT

    // ✅ Unit Testing
    testImplementation("junit:junit:4.13.2") // Direct version for JUnit

    // ✅ Android Instrumentation Testing
    androidTestImplementation("androidx.test.ext:junit:1.1.5") // Direct version for Android JUnit
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1") // Direct version for Espresso
}
