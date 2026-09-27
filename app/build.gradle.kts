import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// ---------------------------------------------------------------------------
// Release signing (CI): when the four signing secrets are present as
// environment variables, a keystore is materialised and used for release.
// Locally (no env), release builds fall back to the debug key so that
// `assembleRelease` always produces an installable artifact.
// ---------------------------------------------------------------------------
val ciKeystoreFile = layout.buildDirectory.file("ci-release.keystore")
val hasCiSigning = listOf(
    "SHINIGAMI_KEYSTORE_BASE64",
    "SHINIGAMI_KEYSTORE_PASSWORD",
    "SHINIGAMI_KEY_ALIAS",
    "SHINIGAMI_KEY_PASSWORD",
).all { System.getenv(it) != null }

if (hasCiSigning) {
    val keystoreBytes = Base64.getDecoder()
        .decode(System.getenv("SHINIGAMI_KEYSTORE_BASE64").trim())
    val keystorePath = ciKeystoreFile.get().asFile
    keystorePath.parentFile.mkdirs()
    keystorePath.writeBytes(keystoreBytes)
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingStoreFile(): Any? = when {
    hasCiSigning -> ciKeystoreFile.get().asFile
    keystoreProps.isNotEmpty() -> keystoreProps.getProperty("storeFile")
    else -> null // fall back to debug keystore
}

android {
    namespace = "com.invokeil.shinigami"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.invokeil.shinigami"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName(
                if (hasCiSigning || keystoreProps.isNotEmpty()) "release" else "debug",
            )
        }
    }

    signingConfigs {
        if (hasCiSigning || keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = signingStoreFile() as java.io.File?
                storePassword = System.getenv("SHINIGAMI_KEYSTORE_PASSWORD")
                    ?: keystoreProps.getProperty("storePassword")
                keyAlias = System.getenv("SHINIGAMI_KEY_ALIAS")
                    ?: keystoreProps.getProperty("keyAlias")
                keyPassword = System.getenv("SHINIGAMI_KEY_PASSWORD")
                    ?: keystoreProps.getProperty("keyPassword")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
        }
    }
    lint {
        warningsAsErrors = false
        abortOnError = true
        checkReleaseBuilds = false
        disable += "Instantiatable"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.work.runtime)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.json)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
