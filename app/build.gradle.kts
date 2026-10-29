plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.noxapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.noxapp"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation(platform("com.google.firebase:firebase-bom:35.0.0"))

    implementation("com.google.firebase:firebase-auth")       // signup / login / verification
    implementation("com.google.firebase:firebase-firestore")  // users/{uid} profile docs
    implementation("com.google.firebase:firebase-storage")  //for images and videos
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2") // lets us .await() Firebase calls
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4") // lifecycleScope
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}