plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val stableSigningStorePath = System.getenv("NOREEL_KEYSTORE_PATH")
val stableSigningStorePassword = System.getenv("NOREEL_KEYSTORE_PASSWORD")
val stableSigningKeyAlias = System.getenv("NOREEL_KEY_ALIAS")
val stableSigningKeyPassword = System.getenv("NOREEL_KEY_PASSWORD")
val hasStableSigning = listOf(
    stableSigningStorePath,
    stableSigningStorePassword,
    stableSigningKeyAlias,
    stableSigningKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.kingxkingt.noreel"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kingxkingt.noreel"
        minSdk = 23
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0"
    }

    if (hasStableSigning) {
        signingConfigs {
            create("stable") {
                storeFile = file(stableSigningStorePath!!)
                storePassword = stableSigningStorePassword
                keyAlias = stableSigningKeyAlias
                keyPassword = stableSigningKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            // CI supplies the same private keystore for every build.
            // Local builds without these environment variables keep Android's normal debug key.
            if (hasStableSigning) {
                signingConfig = signingConfigs.getByName("stable")
            }
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
