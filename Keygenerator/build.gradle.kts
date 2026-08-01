plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseStorePath = providers.environmentVariable("AV_KEYGEN_KEYSTORE").orNull
val releaseStorePassword = providers.environmentVariable("AV_KEYGEN_STORE_PASSWORD").orNull
val releaseSigningConfigured = releaseStorePath != null || releaseStorePassword != null
require(!releaseSigningConfigured || releaseStorePath != null && releaseStorePassword != null) {
    "Teamleiter-Keygenerator production signing requires keystore and password"
}

android {
    namespace = "de.postkisten.keygenerator"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.postkisten.keygenerator"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("production") {
                storeFile = file(requireNotNull(releaseStorePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = "teamleiter-keygenerator"
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
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test>().configureEach {
    maxHeapSize = "128m"
    maxParallelForks = 1
    jvmArgs("-XX:+UseSerialGC", "-Xms32m")
}
