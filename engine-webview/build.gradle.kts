plugins {
    alias(libs.plugins.android.library)
}

// The system WebView backend.
android {
    namespace = "dev.ujhhgtg.via.engine.webview"
    compileSdk = 37

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
    implementation(libs.androidx.webkit)
    implementation(libs.okhttp)
}
