plugins {
    alias(libs.plugins.agp.app)
}
android {
    namespace = "com.fairyxh.hotspotturboxposed"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "com.fairyxh.hotspotturboxposed"
        minSdk = 30
        //noinspection OldTargetApi
        targetSdk = 36
        versionName = "1.0.0"
        versionCode = 100
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles("proguard-rules.pro")
            signingConfig = signingConfigs["debug"]
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += "**"
        }
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}
dependencies {
    compileOnly(libs.libxposed.api)
}