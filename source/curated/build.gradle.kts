plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "fr.cyberagenda.source.curated"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
        // URL distante de la sélection signée (vide = données embarquées uniquement).
        // À définir dans gradle.properties : cyberagenda.curatedUrl=https://raw.githubusercontent.com/<compte>/<depot>/main/curated/events.json
        val url = (project.findProperty("cyberagenda.curatedUrl") as String?).orEmpty()
        buildConfigField("String", "CURATED_URL", "\"$url\"")
    }
    buildFeatures { buildConfig = true }
    // Les tests vérifient la signature du vrai fichier embarqué.
    sourceSets.getByName("test").resources.directories.add("src/main/assets/curated")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(libs.tink.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
}
