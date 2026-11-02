plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Numéro de version : fourni par la CI (numéro du run), sinon 1 en local.
val reculVersionCode = (System.getenv("RECUL_VERSION_CODE") ?: "1").toInt()

android {
    namespace = "fr.niakimbo.recul"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.niakimbo.recul"
        minSdk = 29
        targetSdk = 35
        versionCode = reculVersionCode
        versionName = "0.1.$reculVersionCode"
    }

    // Clé de signature fournie par la CI (secrets) ou en local ; sinon clé de debug.
    val ks = System.getenv("RECUL_KEYSTORE")?.let { file(it) }?.takeIf { it.length() > 0 }
    signingConfigs {
        if (ks != null) create("release") {
            storeFile = ks
            storePassword = System.getenv("RECUL_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("RECUL_KEY_ALIAS")
            keyPassword = System.getenv("RECUL_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (ks != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
