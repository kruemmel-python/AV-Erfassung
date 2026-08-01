import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "de.av.modular.capture"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "de.av.modular.capture"
        minSdk = 26
        targetSdk = 36
        versionCode = 10001
        versionName = "1.0.0-RC1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val releaseKeystorePath = providers.environmentVariable("AVM_ANDROID_KEYSTORE").orNull
    if (releaseKeystorePath != null) {
        val releaseStorePassword = providers.environmentVariable("AVM_ANDROID_STORE_PASSWORD").orNull
        val releaseKeyAlias = providers.environmentVariable("AVM_ANDROID_KEY_ALIAS").orNull
        val releaseKeyPassword = providers.environmentVariable("AVM_ANDROID_KEY_PASSWORD").orNull
        require(!releaseStorePassword.isNullOrBlank()) { "AVM_ANDROID_STORE_PASSWORD fehlt" }
        require(!releaseKeyAlias.isNullOrBlank()) { "AVM_ANDROID_KEY_ALIAS fehlt" }
        require(!releaseKeyPassword.isNullOrBlank()) { "AVM_ANDROID_KEY_PASSWORD fehlt" }
        val avmRelease = signingConfigs.create("avmRelease") {
            storeFile = file(releaseKeystorePath)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
        buildTypes.getByName("release").signingConfig = avmRelease
    }

    buildFeatures { compose = true }
    lint {
        abortOnError = true
        warningsAsErrors = true
    }
    sourceSets["main"].assets.srcDirs("../../modules", "../profiles")
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":platform-core"))
    implementation(project(":avm-canonical"))
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

tasks.withType<Test>().configureEach {
    maxHeapSize = "128m"
    maxParallelForks = 1
    jvmArgs("-XX:+UseSerialGC", "-Xms32m")
}
