plugins {
    alias(libs.plugins.android.library)
}

// Engine-neutral contracts between the app and a browser engine backend.
android {
    namespace = "dev.ujhhgtg.via.engine"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
