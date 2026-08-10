import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// jvmTarget вместо toolchain: сборщику достаточно любого JDK >= 17
// (toolchain 17 валил fdroid build — их окружение без авто-провижининга)
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(libs.junit)
}
