import java.util.Properties
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Signature de release (jamais versionnée) :
// - dossier des clés : CYBERAGENDA_SIGNING_DIR, sinon signing/ (à placer de préférence HORS du projet) ;
// - mots de passe : variables d'environnement CYBERAGENDA_STORE_PASSWORD / CYBERAGENDA_KEY_PASSWORD
//   en priorité, sinon keystore.properties (à vider des mots de passe dès que possible).
val signingDir = providers.environmentVariable("CYBERAGENDA_SIGNING_DIR").orNull?.let(::File)
    ?: rootProject.file("signing")
val releaseSigning = File(signingDir, "keystore.properties").takeIf { it.exists() }?.let { f ->
    Properties().apply { f.inputStream().use { load(it) } }
}
fun signingSecret(env: String, key: String): String? =
    providers.environmentVariable(env).orNull ?: releaseSigning?.getProperty(key)

android {
    namespace = "fr.cyberagenda"
    compileSdk = 37

    defaultConfig {
        applicationId = "fr.cyberagenda"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                // storeFile : nom du keystore dans le dossier des clés (ancien format « signing/x.jks » accepté).
                storeFile = File(signingDir, File(releaseSigning.getProperty("storeFile")).name)
                storePassword = signingSecret("CYBERAGENDA_STORE_PASSWORD", "storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = signingSecret("CYBERAGENDA_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    // Un APK par architecture (MapLibre embarque du code natif) + un APK universel.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            // Réduction + obfuscation du code et suppression des ressources inutilisées.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin")
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":core:geo")) // préchargement de la table des communes au démarrage
    implementation(project(":feature:events"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:calendar"))
    implementation(project(":feature:map"))
    implementation(project(":feature:reminders"))
    // Sources d'événements : ajouter ici chaque nouveau module :source:*
    implementation(project(":source:ctftime"))
    implementation(project(":source:agendadulibre"))
    implementation(project(":source:frcyberevents"))
    implementation(project(":source:confstech"))
    implementation(project(":source:curated"))
    implementation(project(":source:openagenda"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
