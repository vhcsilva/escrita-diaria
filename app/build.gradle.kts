plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.escritadiaria.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.escritadiaria.app"
        minSdk = 26
        targetSdk = 35
        // No GitHub Actions o número do build vira o versionCode, então cada APK novo é uma atualização.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        versionCode = 100 + (build ?: 0)
        versionName = "1.2" + (build?.let { " ($it)" } ?: "")
    }

    // Chave fixa: sem ela cada build teria uma assinatura diferente e o Android exigiria desinstalar.
    // No GitHub Actions ela vem dos secrets SIGNING_KEY (base64 do .jks) e SIGNING_PASSWORD.
    val keyFile = System.getenv("SIGNING_KEY_FILE")?.let { file(it) }?.takeIf { it.exists() }
    val keyPassword = System.getenv("SIGNING_PASSWORD")
    signingConfigs {
        if (keyFile != null && !keyPassword.isNullOrBlank()) {
            create("release") {
                storeFile = keyFile
                storePassword = keyPassword
                keyAlias = "escrita"
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    // O ML Kit traz uma versão antiga do Fragment; a 1.3+ é exigida pelo registerForActivityResult.
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("com.google.mlkit:digital-ink-recognition:18.1.0")
}
