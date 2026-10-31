import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing comes from the environment (CI) or a git-ignored android/keystore.properties; with neither,
// the release build is simply left unsigned so debug and test builds keep working.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(prop)

val releaseStoreFile = signingValue("ANDROID_KEYSTORE_PATH", "storeFile")
val releaseStorePassword = signingValue("ANDROID_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("ANDROID_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("ANDROID_KEY_PASSWORD", "keyPassword") ?: releaseStorePassword
val canSignRelease = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { it != null }

// versionCode is derived from versionName (0.2.1 -> 201) so it always rises with the version, and the CI tag
// check (android-vX.Y.Z must equal appVersionName) keeps the update checker honest.
val appVersionName = "0.1.0"
val appVersionCode = appVersionName.split(".").map(String::toInt).let { (major, minor, patch) -> major * 10000 + minor * 100 + patch }

android {
    namespace = "app.kron.lyrics"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.kron.lyrics"
        minSdk = 31
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (canSignRelease) signingConfig = signingConfigs.getByName("release")
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
        buildConfig = true
    }

    sourceSets {
        getByName("main").java.srcDir("src/main/kotlin")
        getByName("debug").java.srcDir("src/debug/kotlin")
        getByName("test").java.srcDir("src/test/kotlin")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.service)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
