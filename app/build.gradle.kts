plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.autographer.test"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.autographer.test"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    // AAR transitive dependencies
    implementation(libs.kotlin.stdlib)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
}
