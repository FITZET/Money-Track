import java.util.Properties

plugins {
    id("com.android.application")
}

val releaseSigning = Properties().apply {
    val projectFile = rootProject.file("keystore.properties")
    val privateFile = File(System.getProperty("user.home"), ".moneytrack/keystore.properties")
    val propertiesFile = if (projectFile.exists()) projectFile else privateFile
    if (propertiesFile.exists()) propertiesFile.inputStream().use(::load)
}

android {
    namespace = "com.moneytrack.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.moneytrack.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "1.1.2"

        testInstrumentationRunner = "android.app.InstrumentationTestRunner"
    }

    signingConfigs {
        create("release") {
            if (releaseSigning.isNotEmpty()) {
                storeFile = file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
