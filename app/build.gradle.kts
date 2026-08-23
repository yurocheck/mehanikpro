plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.mehanikpro"
    compileSdk = 37  // ← ИСПРАВЛЕНО (была ошибка в синтаксисе)

    defaultConfig {
        applicationId = "com.example.mehanikpro"
        minSdk = 24
        targetSdk = 37  // ← ИСПРАВЛЕНО (рекомендуется 37)
        versionCode = 50
        versionName = "1.0.49"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false  // ← ИСПРАВЛЕНО
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Для HTTP-запросов (проверка обновлений)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Для работы с JSON
    implementation("org.json:json:20240303")

    // Для корутин
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")


    // 👇 ЭТО ДОБАВИТЬ ДЛЯ ИКОНОК
    implementation("androidx.compose.material:material-icons-extended:1.7.5")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
}