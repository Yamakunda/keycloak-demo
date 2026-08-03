plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.snp.bookstore"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.snp.bookstore"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Deep link Keycloak redirect quay lại app sau khi login trên Custom Tab.
        // Phải khớp với redirectUris của client "passwordless-demo" trong Keycloak.
        manifestPlaceholders["appAuthRedirectScheme"] = "com.snp.bookstore"

        // KEYCLOAK_BASE_URL bắt buộc là HTTPS thật (domain ngrok) — Chrome trên Android
        // KHÔNG coi 10.0.2.2/IP LAN là secure context nên WebAuthn/passkey sẽ bị chặn nếu không HTTPS.
        // BOOKSTORE_API_BASE_URL không làm WebAuthn nên không cần HTTPS — trên thiết bị thật
        // (không phải emulator) dùng IP LAN của máy tính, điện thoại phải cùng mạng WiFi.
        buildConfigField("String", "KEYCLOAK_BASE_URL", "\"${project.findProperty("kcBaseUrl") ?: "https://natant-kinesically-easter.ngrok-free.dev"}\"")
        buildConfigField("String", "KEYCLOAK_REALM", "\"test\"")
        buildConfigField("String", "KEYCLOAK_CLIENT_ID", "\"passwordless-demo\"")
        buildConfigField("String", "BOOKSTORE_API_BASE_URL", "\"${project.findProperty("apiBaseUrl") ?: "http://192.168.91.213:3043"}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Custom Tabs + AppAuth: OAuth2/OIDC Authorization Code + PKCE qua trình duyệt thật
    // (bắt buộc để WebAuthn/passkey của Keycloak hoạt động).
    implementation("androidx.browser:browser:1.8.0")
    implementation("net.openid:appauth:0.11.1")

    // Gọi bookstore-api-mobile bằng Bearer token
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
}
