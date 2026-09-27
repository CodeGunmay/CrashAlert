plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.crashalert.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.crashalert.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        val projectId = providers.gradleProperty("crashalertFirebaseProjectId").orElse("").get()
        val appId = providers.gradleProperty("crashalertFirebaseAppId").orElse("").get()
        val apiKey = providers.gradleProperty("crashalertFirebaseApiKey").orElse("").get()
        val endpoint = providers.gradleProperty("crashalertApiUrl").orElse("").get()
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"$projectId\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"$appId\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"$apiKey\"")
        buildConfigField("String", "API_URL", "\"$endpoint\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.12.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core:1.16.0")
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
