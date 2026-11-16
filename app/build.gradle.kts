import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val tmdbKey: String = (localProps.getProperty("TMDB_API_KEY") ?: "").trim()
// Необязательный ключ OMDb (omdbapi.com) — для рейтинга IMDb.
val omdbKey: String = (localProps.getProperty("OMDB_API_KEY") ?: "").trim()

// Ключ подписи релиза: keystore.properties в корне проекта (не попадает в git).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.kinoteka.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kinoteka.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.4.1"
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbKey\"")
        buildConfigField("String", "OMDB_API_KEY", "\"$omdbKey\"")
        manifestPlaceholders["appLabel"] = "@string/app_name"
    }

    signingConfigs {
        if (!keystoreProps.isEmpty) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (!keystoreProps.isEmpty) signingConfig = signingConfigs.getByName("release")
            // R8: ужимает и оптимизирует код, убирает неиспользуемые ресурсы.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Для замеров скорости: как релиз, но с отладочной подписью и отдельным пакетом,
        // чтобы ставиться рядом с основной версией.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".bench"
            manifestPlaceholders["appLabel"] = "Кінотека (тест)"
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // В обычных тестах на компьютере Android-классы — заглушки; пусть возвращают значения по умолчанию.
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.youtube.player)

    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.glance.appwidget)

    testImplementation(libs.junit)
}
