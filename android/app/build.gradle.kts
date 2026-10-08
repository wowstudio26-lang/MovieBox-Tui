plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

tasks.register<Exec>("buildRustAndroid") {
    workingDir(rootProject.projectDir.parentFile)
    commandLine(
        "cargo", "ndk",
        "-t", "arm64-v8a",
        "-o", file("src/main/jniLibs"),
        "build", "--lib", "--release"
    )
}

tasks.named("preBuild").configure {
    dependsOn("buildRustAndroid")
}

android {
    namespace = "com.wowstudio26.movieboxleo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wowstudio26.movieboxleo"
        minSdk = 26
        targetSdk = 36
    ndkVersion = "29.0.14206865"
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
