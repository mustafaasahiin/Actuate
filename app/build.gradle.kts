plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.actuate.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.actuate.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2.0"
        vectorDrawables { useSupportLibrary = true }
        buildConfigField("String", "SERVER_BASE_URL", "\"https://35.232.148.87.sslip.io:8787\"")
        ndk {
            abiFilters += setOf("arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            val ksPath = (project.findProperty("RELEASE_KEYSTORE_PATH") as? String)
                ?: System.getenv("RELEASE_KEYSTORE_PATH")
                ?: "keystore/actuate-release.keystore"
            val ks = rootProject.file(ksPath)

            val ksPassword = (project.findProperty("RELEASE_KEYSTORE_PASSWORD") as? String)
                ?: System.getenv("RELEASE_KEYSTORE_PASSWORD")
                ?: rootProject.file("keystore/keystore-password.txt").takeIf { it.exists() }?.readText()?.trim()

            val alias = (project.findProperty("RELEASE_KEY_ALIAS") as? String)
                ?: System.getenv("RELEASE_KEY_ALIAS")
                ?: "actuate"

            val keyPasswordVal = (project.findProperty("RELEASE_KEY_PASSWORD") as? String)
                ?: System.getenv("RELEASE_KEY_PASSWORD")
                ?: ksPassword

            if (ks.exists() && !ksPassword.isNullOrBlank()) {
                storeFile = ks
                storePassword = ksPassword
                keyAlias = alias
                keyPassword = keyPasswordVal
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            val localDev = project.findProperty("USE_LOCAL_SERVER") == "true"
            val debugUrl = if (localDev) "http://10.0.2.2:8787" else "https://35.232.148.87.sslip.io:8787"
            buildConfigField("String", "SERVER_BASE_URL", "\"$debugUrl\"")
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

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        // Zero-lint gate: the release build must not ship with errors.
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
        checkDependencies = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":domain"))
    implementation(project(":data"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.revenuecat)
    implementation(libs.google.play.services.auth)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}