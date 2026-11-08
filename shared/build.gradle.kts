import org.jetbrains.kotlin.gradle.dsl.JvmTarget
plugins {
    alias(libs.plugins.realm)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp)
}
kotlin {
    androidLibrary {
        namespace = "com.example.kmmbase.shared"
        compileSdk = 35
        minSdk = 24
        compilations.configureEach {
            compilerOptions.configure { jvmTarget.set(JvmTarget.JVM_17) }
        }
        withHostTestBuilder {}.configure {}
    }
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    jvmToolchain(17)
    sourceSets {
        commonMain.dependencies {
            implementation(libs.realm.base)
            api(libs.coroutines.core) // Flow and StateFlow are part of the public API.
        }
        androidMain.dependencies {
            implementation(libs.coroutines.android) // Provides Dispatchers.Main.
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
    }
}
