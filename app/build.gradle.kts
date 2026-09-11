plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.jobregister.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jobregister.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    flavorDimensions += "role"
    productFlavors {
        create("admin") {
            dimension = "role"
            applicationIdSuffix = ".admin"
            resValue("string", "app_name", "MH Job Register Admin")
            buildConfigField("String", "ROLE", "\"ADMIN\"")
        }
        create("cleaner") {
            dimension = "role"
            applicationIdSuffix = ".cleaner"
            resValue("string", "app_name", "MH Job Register Cleaner")
            buildConfigField("String", "ROLE", "\"CLEANER\"")
        }
        create("repairer") {
            dimension = "role"
            applicationIdSuffix = ".repairer"
            resValue("string", "app_name", "MH Job Register Repairer")
            buildConfigField("String", "ROLE", "\"REPAIRER\"")
        }
        create("initiator") {
            dimension = "role"
            applicationIdSuffix = ".initiator"
            resValue("string", "app_name", "MH Job Register Initiator")
            buildConfigField("String", "ROLE", "\"INITIATOR\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Debug-signed so the release APKs are directly installable for this
            // internal tool. Replace with a real keystore before Play Store upload.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
