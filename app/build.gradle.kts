plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val releaseStorePath = providers.environmentVariable("AV_ERFASSUNG_KEYSTORE").orNull
val releaseStorePassword = providers.environmentVariable("AV_ERFASSUNG_STORE_PASSWORD").orNull
val releaseSigningConfigured = releaseStorePath != null || releaseStorePassword != null
require(!releaseSigningConfigured || releaseStorePath != null && releaseStorePassword != null) {
    "AV-Erfassung production signing requires keystore and password"
}

android {
    namespace = "de.postkisten.tracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.postkisten.tracker"
        minSdk = 26
        targetSdk = 37
        versionCode = 12
        versionName = "2.1.0"
        resValue("string", "build_id", "20260801-02")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = false
        resValues = true
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("production") {
                storeFile = file(requireNotNull(releaseStorePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = "av-erfassung"
                keyPassword = releaseStorePassword
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("production")?.let { signingConfig = it }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    lint {
        abortOnError = true
        warningsAsErrors = true
        checkReleaseBuilds = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.core:core-ktx:1.19.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

tasks.withType<Test>().configureEach {
    maxHeapSize = "128m"
    maxParallelForks = 1
    jvmArgs("-XX:+UseSerialGC", "-Xms32m")
}
