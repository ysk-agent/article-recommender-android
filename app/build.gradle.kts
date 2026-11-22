plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.moriya.discover"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.moriya.discover"
        minSdk = 29
        targetSdk = 35
        versionCode = 4
        versionName = "0.1.3"
    }

    // Pinned signing key, same pattern as the other ysk-agent apps:
    // CI decodes the keystore from the SIGNING_KEYSTORE_B64 repo secret
    // and points SIGNING_KEYSTORE at it, so every build carries the same
    // certificate and can overwrite-install over previous builds.
    signingConfigs {
        create("pinned") {
            val keystorePath = System.getenv("SIGNING_KEYSTORE")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SIGNING_STORE_PASS")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASS")
            }
        }
    }

    buildTypes {
        debug {
            if (System.getenv("SIGNING_KEYSTORE") != null) {
                signingConfig = signingConfigs.getByName("pinned")
            }
        }
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Deliberately minimal, matching the sibling apps (yt-client,
// apps-updater): appcompat + recyclerview only, UI built
// programmatically — no Material, no ViewBinding, no generated R
// references in code. This is what makes the VM-side kotlinc compile
// check (ci/kotlin-check.sh) possible without Gradle.
dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
