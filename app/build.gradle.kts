plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.mehmetarif.sadegorev"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mehmetarif.sadegorev"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "2.0"
    }
    // signing/tasklist.keystore depoya eklenmez (.gitignore). Dosya yerelde varsa
    // önceki APK'larla aynı imza kullanılır; yoksa debug derleme varsayılan debug
    // anahtarıyla imzalanır.
    val tasklistKeystore = rootProject.file("signing/tasklist.keystore")
    signingConfigs {
        if (tasklistKeystore.exists()) create("tasklist") {
            storeFile = tasklistKeystore
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        debug {
            if (tasklistKeystore.exists()) signingConfig = signingConfigs.getByName("tasklist")
        }
        release {
            isMinifyEnabled = false
            if (tasklistKeystore.exists()) signingConfig = signingConfigs.getByName("tasklist")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation("com.google.android.gms:play-services-auth:22.0.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    val composeBom = platform("androidx.compose:compose-bom:2026.04.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    kapt("androidx.room:room-compiler:2.8.5")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    // Ekran görüntüsü testleri (ScreenshotTest; yalnızca -Pscreenshots ile çalışır)
    testImplementation(composeBom)
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test.ext:junit:1.2.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.withType<Test>().configureEach {
    // Ekran görüntüsü testleri yalnızca -Pscreenshots=1 verildiğinde çalışır
    if (!project.hasProperty("screenshots")) exclude("**/screenshots/**")
    systemProperty("screenshots", (project.findProperty("screenshots") ?: "").toString())
    systemProperty("screenshotDir", rootProject.file("build/screenshots").absolutePath)
    systemProperty("screenshotPrefix", (project.findProperty("screenshotPrefix") ?: "").toString())
    // İsteğe bağlı: Robolectric'in Android jar'ını önceden indirilmiş klasörden kullan
    project.findProperty("robolectricDir")?.let {
        systemProperty("robolectric.offline", "true")
        systemProperty("robolectric.dependency.dir", it.toString())
    }
}

// AGP 8.13.2 profgen requires a fresh metadata directory on incremental builds.
// Only generated profile output is removed; application data is never involved.
tasks.matching { it.name == "compileReleaseArtProfile" }.configureEach {
    doFirst {
        delete(layout.buildDirectory.dir("intermediates/dex_metadata_directory/release/compileReleaseArtProfile"))
    }
}
