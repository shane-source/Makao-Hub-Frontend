plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}


android {
    namespace = "com.makaohub.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.makaohub.android"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures { compose = true; buildConfig = true }
    val mockPayments = providers.gradleProperty("MAKAO_MOCK_PAYMENTS_ENABLED").orElse("true").get()
    require(mockPayments == "true" || mockPayments == "false")
    buildTypes {
        getByName("debug") { buildConfigField("boolean", "MOCK_PAYMENTS_ENABLED", mockPayments) }
        getByName("release") { buildConfigField("boolean", "MOCK_PAYMENTS_ENABLED", "false") }
    }

    val apiUrl = providers.gradleProperty("MAKAO_API_BASE_URL").orElse("http://10.0.2.2:8080/").get()
    val googleClientId = providers.gradleProperty("MAKAO_GOOGLE_WEB_CLIENT_ID").orElse("").get()
    require(apiUrl.endsWith("/") && (apiUrl.startsWith("https://") || apiUrl.startsWith("http://")))
    require(!apiUrl.contains('"') && !apiUrl.contains('\\') && !apiUrl.contains('\n'))
    require(googleClientId.matches(Regex("[A-Za-z0-9._-]*")))
    defaultConfig {
        buildConfigField("String", "API_BASE_URL", "\"$apiUrl\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleClientId\"")
    }


    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("org.maplibre.gl:android-sdk-opengl:13.6.1")
    implementation("androidx.camera:camera-camera2:1.5.3")
    implementation("androidx.camera:camera-lifecycle:1.5.3")
    implementation("androidx.camera:camera-view:1.5.3")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
