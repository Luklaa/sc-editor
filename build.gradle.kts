plugins {
    kotlin("multiplatform") version "2.0.0"
    id("org.jetbrains.compose") version "1.6.11"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
    id("com.android.application") version "8.2.2"
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions { jvmTarget = "17" }
        }
    }

    jvm("desktop") {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.materialIconsExtended)
                implementation(files("libs/libktx-5.0.0.jar"))
                // zstd-jni исключаем из транзитивных зависимостей: на desktop нужен обычный jar,
                // на Android - @aar с нативными библиотеками (см. desktopMain / androidMain).
                implementation("dev.donutquine:supercell-texture:1.0.1") {
                    exclude(group = "com.github.luben", module = "zstd-jni")
                }
                implementation("dev.donutquine:supercell-swf:1.1.8") {
                    exclude(group = "com.github.luben", module = "zstd-jni")
                }
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("androidx.activity:activity-compose:1.9.0")
                implementation("com.github.luben:zstd-jni:1.5.7-7@aar")
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")
                implementation("com.formdev:flatlaf:3.7.1")
                implementation("com.github.luben:zstd-jni:1.5.7-7")
            }
        }
    }
}

android {
    namespace = "com.luklaaa.sceditor"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.luklaaa.sceditor"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.6.2"

        // libktx.so лежит только для arm64-v8a - не собираем остальные ABI,
        // иначе на них приложение запустится без нативной библиотеки.
        ndk { abiFilters += "arm64-v8a" }
    }

    sourceSets["main"].apply {
        manifest.srcFile("src/androidMain/AndroidManifest.xml")
        res.srcDirs("src/androidMain/res")
        jniLibs.srcDirs("src/androidMain/jniLibs")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "SCEditor"
            packageVersion = "1.6.2"
        }
    }
}
