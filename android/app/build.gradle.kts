import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.navigation.safe.args)
    alias(libs.plugins.google.services)
}

// Alamat API per orang: isi `api.baseUrl` di local.properties (tidak masuk git). Bawaan 10.0.2.2
// = localhost komputer dari emulator Android; port 3000 = port bawaan backend.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val apiBaseUrl = (localProperties.getProperty("api.baseUrl") ?: "http://100.122.168.100:3005/api/v1/")
    .trim()
    .let { if (it.endsWith("/")) it else "$it/" } // Retrofit mewajibkan akhiran "/"

android {
    namespace = "id.sch.santaagnes.sim"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "id.sch.santaagnes.sim"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "BASE_URL",
            "\"$apiBaseUrl\""
        )
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    sourceSets {
        // Template impor Excel diunduh dari aplikasi; sumbernya satu: docs/import-templates
        getByName("main").assets.srcDir("../../docs/import-templates")
    }

    androidResources {
        // Pola bawaan AGP + berkas kunci Excel (~$...) dan README yang ikut dari docs/import-templates
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:<dir>_*:CVS:thumbs.db:picasa.ini:*~:~*:README.md"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        // java.time dipakai di semua API; min SDK 24 belum memilikinya secara bawaan
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.recyclerview)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // REST API
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // Local session storage
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Firebase Cloud Messaging (NTF-02); versi diatur BoM
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Navigation Component
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.fragment.ktx)

    // ViewModel dan Lifecycle
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)

    // Room Database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    androidTestImplementation(
        "androidx.test:core-ktx:1.7.0"
    )
}