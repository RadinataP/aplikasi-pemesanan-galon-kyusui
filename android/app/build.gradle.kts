import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// API URL dapat dioverride tanpa mengubah source atau menyimpan alamat lokal
// developer ke dalam repository. Prioritas: -P Gradle, local.properties, default.
val kyusuiLocalProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
fun buildConfigString(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
val debugApiBaseUrl = providers.gradleProperty("kyusuiApiBaseUrl")
    .orElse(kyusuiLocalProperties.getProperty("KYUSUI_API_BASE_URL") ?: "http://10.0.2.2:8000/api/v1/")
    .get()
val releaseApiBaseUrl = providers.gradleProperty("kyusuiProdApiBaseUrl")
    .orElse(kyusuiLocalProperties.getProperty("KYUSUI_PROD_API_BASE_URL") ?: "https://api.kyusui.example.com/api/v1/")
    .get()

require(debugApiBaseUrl.endsWith("/")) {
    "KYUSUI_API_BASE_URL harus diakhiri garis miring (/) untuk Retrofit."
}
require(debugApiBaseUrl.startsWith("http://") || debugApiBaseUrl.startsWith("https://")) {
    "KYUSUI_API_BASE_URL harus berupa URL HTTP(S) lengkap."
}
require(releaseApiBaseUrl.startsWith("https://") && releaseApiBaseUrl.endsWith("/")) {
    "KYUSUI_PROD_API_BASE_URL harus berupa URL HTTPS lengkap dan diakhiri /."
}

// URL contoh tetap diperbolehkan saat kompilasi debug, tetapi tidak boleh masuk
// ke artefak release. URL produksi diberikan melalui -PkyusuiProdApiBaseUrl atau
// local.properties (file lokal ini tidak dibagikan).
fun validateReleaseApiBaseUrl() {
    val host = runCatching {
        URI.create(releaseApiBaseUrl).host?.lowercase()
    }.getOrNull()
    require(!host.isNullOrBlank() && host != "example.com" && !host.endsWith(".example.com") && !host.endsWith(".example")) {
        "Build release diblokir: atur KYUSUI_PROD_API_BASE_URL ke host HTTPS backend produksi yang nyata."
    }
}

tasks.configureEach {
    if (name.contains("Release", ignoreCase = true) &&
        (name.startsWith("assemble") || name.startsWith("bundle") || name.startsWith("package"))) {
        doFirst { validateReleaseApiBaseUrl() }
    }
}

android {
    namespace = "com.kyusui.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.kyusui.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            buildConfigField("String", "API_BASE_URL", buildConfigString(releaseApiBaseUrl))
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            buildConfigField("String", "API_BASE_URL", buildConfigString(debugApiBaseUrl))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,LICENSE,NOTICE}"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}


kotlin {
    compilerOptions {
        jvmTarget.set(
            org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        )
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.compose.bom))

    // Material 3
    implementation(libs.material3)
    implementation(libs.material3.window.size)
    implementation(libs.material.icons.extended)

    // Navigation
    implementation(libs.navigation.compose)

    // Retrofit & OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    // DataStore
    implementation(libs.datastore.preferences)
    implementation(libs.datastore.rxjava3)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.test)

    // Kotlinx Datetime
    implementation(libs.kotlinx.datetime)

    // Coil (Image loading)
    implementation(libs.coil.compose)

    // Hilt (DI)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // MapLibre merender peta; play-services-location tetap dipakai untuk GPS kurir.
    implementation(libs.maplibre.android)
    implementation(libs.play.services.location)

    // Firebase Cloud Messaging (Phase 8 - notification transport)
    // FCM hanya transport; backend tetap source of truth.
    // Tanpa google-services.json, Firebase tidak terinisialisasi dan FCM
    // otomatis nonaktif. Lihat KyusuiApplication dan FcmMessageTransport.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.robolectric)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }
}
