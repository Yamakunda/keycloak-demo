plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
}

android {
    namespace = "com.snp.bookstorebio"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.snp.bookstorebio"
        minSdk = 28 // BiometricPrompt.CryptoObject + Keystore user-auth-bound key ổn định nhất từ API 28+
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Deep link Keycloak redirect quay lại app sau khi login trên Custom Tab.
        // Phải khớp với redirectUris của client "biometric-demo" trong Keycloak.
        manifestPlaceholders["appAuthRedirectScheme"] = "com.snp.bookstorebio"

        // KEYCLOAK_BASE_URL bắt buộc là HTTPS thật (domain ngrok) chỉ cho LẦN ĐĂNG NHẬP ĐẦU
        // (mở Custom Tab, username/password bình thường — client này không dùng WebAuthn).
        // Các lần sau app dùng vân tay cục bộ để giải mã refresh_token đã lưu, không mở
        // lại Custom Tab, nên vẫn cần Keycloak đạt được qua mạng chỉ để gọi token endpoint.
        buildConfigField("String", "KEYCLOAK_BASE_URL", "\"${project.findProperty("kcBaseUrl") ?: "https://natant-kinesically-easter.ngrok-free.dev"}\"")
        buildConfigField("String", "KEYCLOAK_REALM", "\"test\"")
        buildConfigField("String", "KEYCLOAK_CLIENT_ID", "\"biometric-demo\"")
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

    // Custom Tabs + AppAuth: OAuth2/OIDC Authorization Code + PKCE qua trình duyệt thật.
    // Chỉ dùng ở LẦN ĐĂNG NHẬP ĐẦU để lấy refresh_token ban đầu.
    // Cũng dùng lại Custom Tabs để mở verification_uri_complete khi quét QR đăng nhập chéo
    // thiết bị — cùng cookie SSO với Chrome nên Keycloak nhận diện đã đăng nhập, không cần
    // nhập lại mật khẩu.
    implementation("androidx.browser:browser:1.8.0")
    implementation("net.openid:appauth:0.11.1")

    // Quét QR cho luồng "đăng nhập chéo thiết bị" (OAuth2 Device Authorization Grant):
    // CameraX cho preview camera + ML Kit Barcode Scanning để decode QR trong khung hình.
    implementation("androidx.camera:camera-core:1.4.0")
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Vân tay/Face unlock cục bộ + khóa mã hóa gắn Android Keystore
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    // Gọi bookstore-api-mobile bằng Bearer token
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-android-compiler:2.51.1")
}
