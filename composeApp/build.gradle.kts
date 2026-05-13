plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(17)
    jvm {
        mainRun {
            mainClass.set("com.fortrx.desktop.MainKt")
        }
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
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
                api(libs.datetime)
                implementation(libs.koin.core)
                implementation(libs.koin.compose.multiplatform)
                implementation(libs.kermit)
                implementation(libs.voyager.navigator)
                implementation(libs.voyager.transitions)
                implementation(libs.voyager.screenmodel)
                implementation(libs.voyager.koin)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.coroutines.test)
                implementation(libs.datetime)
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
        val jvmTest by getting {
            dependencies {
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
                implementation(libs.datetime)
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation("org.robolectric:robolectric:4.14.1")
                implementation("androidx.test:core:1.6.1")
            }
        }
        val androidInstrumentedTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.coroutines.test)
                implementation("androidx.test:core:1.6.1")
                implementation("androidx.test:runner:1.6.1")
                implementation("androidx.test.ext:junit:1.2.1")
            }
        }
    }
}

android {
    namespace = "com.fortrx.shared"
    compileSdk = 37
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
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

compose.desktop {
    application {
        mainClass = "com.fortrx.desktop.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi
            )
            packageName = "Fortrx"
            packageVersion = "1.0.0"
        }
    }
}
