import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val secretsFile = rootProject.file("example/secrets.properties")
val secrets = Properties().apply {
    if (secretsFile.exists()) {
        load(FileInputStream(secretsFile))
    }
}

android {
    namespace = "com.xmoney.example"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.xmoney.example"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField(
            "String",
            "PUBLIC_KEY",
            "\"${secrets.getProperty("PUBLIC_KEY", "pk_test_replace_me")}\"",
        )
        buildConfigField(
            "String",
            "API_KEY",
            "\"${secrets.getProperty("API_KEY", "")}\"",
        )
        buildConfigField(
            "String",
            "API_BASE",
            "\"${secrets.getProperty("API_BASE", "https://demo.xmoney.com")}\"",
        )
        buildConfigField(
            "String",
            "CURRENCY",
            "\"${secrets.getProperty("CURRENCY", "EUR")}\"",
        )
        buildConfigField(
            "String",
            "DESCRIPTION",
            "\"${secrets.getProperty("DESCRIPTION", "Embeddable Configuration - Payment Card")}\"",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// Local modules by default. To verify the published Maven artifacts:
//   ./gradlew :example:assembleDebug -PuseMavenSdk
//   ./gradlew :example:assembleDebug -PuseMavenSdk=true
val useMavenSdk = providers.gradleProperty("useMavenSdk")
    .map { it.isEmpty() || it.equals("true", ignoreCase = true) }
    .orElse(false)
    .get()
val publishedSdkVersion = "0.0.3"

dependencies {
    if (useMavenSdk) {
        implementation("com.xmoney:paymentsheet:$publishedSdkVersion")
        implementation("com.xmoney:googlepay:$publishedSdkVersion")
        implementation("com.xmoney:paymentelement:$publishedSdkVersion")
    } else {
        implementation(project(":paymentsheet"))
        implementation(project(":googlepay"))
        implementation(project(":paymentelement"))
    }

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.coroutines.android)
    implementation(libs.okhttp)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
}
