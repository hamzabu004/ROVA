plugins {
    id("com.android.application") version "8.13.0"
}

repositories {
    google()
    mavenCentral()
}

android {
    namespace = "rova.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "rova.android"
        minSdk = 26
        targetSdk = 32
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
