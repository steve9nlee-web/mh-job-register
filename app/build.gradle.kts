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
        versionCode = 18
        versionName = "2.7"

        // Built-in sync server: every APK ships pointing at the shared Job
        // Register backend, so phones sync with zero setup. Both values can
        // still be changed per phone in Settings.
        buildConfigField("String", "DEFAULT_SYNC_URL",
            "\"https://script.google.com/macros/s/AKfycbw4D6mscZV0q_a-gWCzvfa0rr-OV9HOes43pWkJ_S2bJprx-ZwqP8MxLvMTBMoZp6n9/exec\"")
        buildConfigField("String", "DEFAULT_SYNC_KEY", "\"MH-SYNC-2026\"")
    }

    flavorDimensions += "role"
    productFlavors {
        create("admin") {
            dimension = "role"
            applicationIdSuffix = ".admin"
            resValue("string", "app_name", "MH Job Register Admin")
            buildConfigField("String", "ROLE", "\"ADMIN\"")
            buildConfigField("boolean", "STAFF_CODE_LOGIN", "false")
        }
        // One app for every executor. The role (Cleaner or Repairer) is not
        // compiled in: each person signs in with their own staff code from
        // the Staff tab of the sheet, and the code decides the role.
        create("contractor") {
            dimension = "role"
            applicationIdSuffix = ".contractor"
            resValue("string", "app_name", "MH Job Register Contractor")
            buildConfigField("String", "ROLE", "\"\"")
            buildConfigField("boolean", "STAFF_CODE_LOGIN", "true")
        }
        create("initiator") {
            dimension = "role"
            applicationIdSuffix = ".initiator"
            resValue("string", "app_name", "MH Job Register Initiator")
            buildConfigField("String", "ROLE", "\"INITIATOR\"")
            buildConfigField("boolean", "STAFF_CODE_LOGIN", "false")
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
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
