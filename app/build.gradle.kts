plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.ujhhgtg.via"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.ujhhgtg.via"
        minSdk = 29
        targetSdk = 37
        versionCode = 20260823
        versionName = "7.3.3"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    var foundKeystore = false

    signingConfigs {
        val storeFilePath = System.getenv("ANDROID_KEYSTORE_FILE")
            ?: runCatching { project.property("ANDROID_KEYSTORE_FILE") }.getOrNull() as? String
        val storePasswordValue = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            ?: runCatching { project.property("ANDROID_KEYSTORE_PASSWORD") }.getOrNull() as? String
        val keyAliasValue = System.getenv("ANDROID_KEY_ALIAS")
            ?: runCatching { project.property("ANDROID_KEY_ALIAS") }.getOrNull() as? String
        val keyPasswordValue = System.getenv("ANDROID_KEY_PASSWORD")
            ?: runCatching { project.property("ANDROID_KEY_PASSWORD") }.getOrNull() as? String

        val keystoreFile = storeFilePath?.let(::file)
        if (keystoreFile?.isFile == true &&
            !storePasswordValue.isNullOrEmpty() &&
            !keyAliasValue.isNullOrEmpty() &&
            !keyPasswordValue.isNullOrEmpty()
        ) {
            create("release") {
                foundKeystore = true
                storeFile = keystoreFile
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName(if (foundKeystore) "release" else "debug")
            optimization.enable = true
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.dynamicanimation)
    implementation(libs.androidx.customview)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.biometric)

    implementation(libs.okhttp)

    implementation(libs.zxing.core)
    implementation(libs.zxing.embedded)
    implementation(libs.tinypinyin)
}
