import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// CI passes -PkronVersion=0.1.0 from the android-v0.1.0 tag; local builds stay at 0.0.0.
val kronVersion = (findProperty("kronVersion") as String?) ?: "0.0.0"
val kronVersionCode = kronVersion.split(".").map { it.toIntOrNull() ?: 0 }
    .let { (it.getOrElse(0) { 0 } * 10_000) + (it.getOrElse(1) { 0 } * 100) + it.getOrElse(2) { 0 } }
    .coerceAtLeast(1)

// The release key comes from the environment (CI secrets), never from the repo.
val keystorePath: String? = System.getenv("KRON_KEYSTORE_PATH")

android {
    namespace = "io.github.arontz.kron"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.arontz.kron"
        minSdk = 29
        targetSdk = 36
        versionCode = kronVersionCode
        versionName = kronVersion
    }

    signingConfigs {
        // A fixed debug key, so each CI test build installs over the last one. Not secret.
        getByName("debug") {
            storeFile = file("debug.keystore")
        }
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KRON_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KRON_KEY_ALIAS")
                keyPassword = System.getenv("KRON_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
