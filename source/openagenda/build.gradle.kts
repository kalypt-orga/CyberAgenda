plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "fr.cyberagenda.source.openagenda"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
        // Clé PUBLIQUE OpenAgenda (lecture seule, publique par conception) : gradle.properties,
        // cyberagenda.openagendaKey=oa_pk_… Une clé secrète (oa_sk_) est refusée : elle donnerait
        // les droits du compte à quiconque lit l'APK.
        val key = (project.findProperty("cyberagenda.openagendaKey") as String?).orEmpty()
        require(key.isEmpty() || Regex("oa_pk_[A-Za-z0-9]{16,128}").matches(key)) {
            "cyberagenda.openagendaKey : seule une clé publique oa_pk_ est acceptée"
        }
        buildConfigField("String", "OPENAGENDA_KEY", "\"$key\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = true }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(project(":core:formats"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
}
