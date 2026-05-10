plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(21)
    jvm {
        mainRun {
            mainClass.set("com.fortrx.desktop.MainKt")
        }
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.material)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)
                implementation(libs.ktor.core)
                implementation(libs.ktor.content)
                implementation(libs.ktor.json)
                implementation(libs.ktor.websockets)
                implementation(libs.ktor.logging)
                implementation(libs.sqldelight.runtime)
                implementation(libs.sqldelight.coroutines)
                implementation(libs.coroutines.core)
                implementation(libs.serialization.json)
                implementation(libs.datetime)
                implementation(libs.koin.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.ktor.cio)
                implementation(libs.sqldelight.jvm)
                implementation(libs.bouncycastle)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.security)
                implementation(libs.ktor.okhttp)
                implementation(libs.sqldelight.android)
                implementation(libs.bouncycastle)
                implementation(libs.coroutines.android)
            }
        }
    }
}

android {
    namespace = "com.fortrx.shared"
    compileSdk = 37
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

sqldelight {
    databases {
        create("FortrxDb") {
            packageName.set("com.fortrx.db")
            dialect("app.cash.sqldelight:sqlite-3-35-dialect:2.3.2")
        }
    }
}
