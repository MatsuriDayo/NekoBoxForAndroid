@file:Suppress("UnstableApiUsage")

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

setupApp()

android {
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
    bundle {
        language {
            enableSplit = false
        }
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
        aidl = true
    }
    namespace = "io.nekohasekai.sagernet"
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    androidResources {
        generateLocaleConfig = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {

    implementation(fileTree("libs"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.fragment:fragment-ktx:1.9.1")
    implementation("androidx.browser:browser:1.10.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.2")
    // Navigation 2.10 requires API 24; keep Android 6 (API 23) support.
    //noinspection GradleDependency
    implementation("androidx.navigation:navigation-fragment-ktx:2.9.8")
    // Navigation 2.10 requires API 24; keep Android 6 (API 23) support.
    //noinspection GradleDependency
    implementation("androidx.navigation:navigation-ui-ktx:2.9.8")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.appcompat:appcompat:1.8.0")
    // WorkManager 2.12 requires API 24; keep Android 6 (API 23) support.
    //noinspection GradleDependency
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    // WorkManager 2.12 requires API 24; keep Android 6 (API 23) support.
    //noinspection GradleDependency
    implementation("androidx.work:work-multiprocess:2.11.2")

    implementation("com.google.android.material:material:1.14.0")
    implementation("com.google.code.gson:gson:2.14.0")

    implementation("com.github.jenly1314:zxing-lite:3.5.0")
    implementation("com.blacksquircle.ui:editorkit:2.9.0")
    implementation("com.blacksquircle.ui:language-base:2.9.0")
    implementation("com.blacksquircle.ui:language-json:2.9.0")

    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("org.yaml:snakeyaml:2.7")
    implementation("com.github.daniel-stoneuk:material-about-library:3.1.2")
    implementation("com.jakewharton:process-phoenix:3.0.0")
    implementation("com.esotericsoftware:kryo:5.7.1")
    implementation("com.google.guava:guava:33.7.2-android")
    implementation("org.ini4j:ini4j:0.5.4")

    implementation("com.simplecityapps:recyclerview-fastscroll:2.0.1") {
        exclude(group = "androidx.recyclerview")
        exclude(group = "androidx.appcompat")
    }

    implementation("androidx.room:room-runtime:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    testImplementation("junit:junit:4.13.2")
}
