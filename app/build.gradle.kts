plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.eirv.androidapiextractor"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.eirv.androidapiextractor"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(libs.smali.dexlib2)
    implementation(libs.guava)
    implementation(libs.asm)
    implementation(libs.asm.tree)
}