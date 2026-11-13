import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.chimafok.todo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.chimafok.todo"
        minSdk = 26
        targetSdk = 37
        versionCode = 43
        versionName = "1.5.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Bundle every supported language for offline switching, including future AAB builds.
    bundle { language { enableSplit = false } }

    buildFeatures {
        compose = true
    }

    // Instrumentation runs against a separate installation so test cleanup
    // cannot uninstall the user's app or touch their todo.db.
    buildTypes {
        // Official releases use com.chimafok.todo.
        // Keep development installs separate from official release data.
        getByName("debug") { applicationIdSuffix = ".debug"; versionNameSuffix = "-dev06" }
        // Optimized comparison build: same update identity as debug, never the official key.
        create("perf") {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev06-perf"
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isProfileable = true
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            matchingFallbacks += listOf("release")
        }
        create("qaPerf") {
            initWith(getByName("perf"))
            applicationIdSuffix = ".qa"
            versionNameSuffix = "-qa-perf"
            matchingFallbacks += listOf("perf", "release")
        }
        create("qa") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".qa"
            versionNameSuffix = "-qa"
            matchingFallbacks += listOf("debug")
        }
    }
    // Keep the comparison build's app label consistent with existing test installs.
    sourceSets.getByName("perf").res.srcDir("src/debug/res")
    sourceSets.getByName("qaPerf").apply {
        manifest.srcFile("src/qa/AndroidManifest.xml")
        kotlin.srcDir("src/qa/java")
    }
    testBuildType = "qa"
    sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// All variants export the same Room schema file. Serialize those writers.
tasks.matching { it.name == "kspQaKotlin" }.configureEach {
    mustRunAfter("kspDebugKotlin")
}

tasks.matching { it.name == "kspReleaseKotlin" }.configureEach {
    mustRunAfter("kspDebugKotlin", "kspQaKotlin")
}

tasks.matching { it.name == "kspPerfKotlin" }.configureEach {
    mustRunAfter("kspDebugKotlin", "kspQaKotlin", "kspReleaseKotlin")
}
tasks.matching { it.name == "kspQaPerfKotlin" }.configureEach {
    mustRunAfter("kspDebugKotlin", "kspQaKotlin", "kspReleaseKotlin", "kspPerfKotlin")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
