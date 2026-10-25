plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.moorty.voice"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.moorty.voice"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0-android-port"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    // Android-native WebSocket client; TikTok connection runs in the separate relay server.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
