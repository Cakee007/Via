plugins {
    alias(libs.plugins.android.library)
}

// The GeckoView backend, with its built-in WebExtension in assets/via-engine.
android {
    namespace = "dev.ujhhgtg.via.engine.gecko"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    api(project(":engine-api"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.geckoview.arm64)
}
