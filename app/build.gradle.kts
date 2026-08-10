plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dmi3dmi3.saywhen"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dmi3dmi3.saywhen"
        minSdk = 26
        targetSdk = 36
        // versionCode: +1 на каждую загрузку в Play (монотонный счётчик);
        // versionName — человекочитаемый тег релиза, поднимать вместе с ним
        versionCode = 15
        versionName = "2.0.1"
    }

    // upload-ключ для Play App Signing; креды — SAYWHEN_* в ~/.gradle/gradle.properties
    // (машино-специфично, вне репо); без них release собирается неподписанным
    val uploadStore = providers.gradleProperty("SAYWHEN_STORE_FILE").orNull
    fun prop(name: String) = providers.gradleProperty(name).orNull
        ?: error("$name не задан — нужны все четыре SAYWHEN_*-свойства")
    signingConfigs {
        if (uploadStore != null) {
            create("upload") {
                storeFile = file(uploadStore)
                storePassword = prop("SAYWHEN_STORE_PASSWORD")
                keyAlias = prop("SAYWHEN_KEY_ALIAS")
                keyPassword = prop("SAYWHEN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload")
        }
    }

    kotlinOptions {
        jvmTarget = "17"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true  // версия в подвале About (задача 21)
    }
}

dependencies {
    implementation(project(":parser"))
    implementation(libs.appcompat)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit)
}
