plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.jartool.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.odys.patcherjar"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.3"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        resources {
            excludes += listOf(
                "META-INF/DEPENDENCIES", "META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/LICENSE.md",
                "META-INF/NOTICE", "META-INF/NOTICE.txt", "META-INF/NOTICE.md", "META-INF/INDEX.LIST",
                "META-INF/AL2.0", "META-INF/LGPL2.1",
            )
        }
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(bom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Dekompilasi dex ke Java (jadx 1.5.2 ke atas sudah mengenal kontainer dex 041)
    implementation("io.github.skylot:jadx-core:1.5.5")
    implementation("io.github.skylot:jadx-dex-input:1.5.5")
}
