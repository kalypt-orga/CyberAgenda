plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "fr.cyberagenda.feature.settings"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
        // Adresse de secours pour les propositions (vide = pas de lien « par e-mail »). Elle est
        // visible dans l'app : utiliser une adresse de contact publique, pas une adresse personnelle.
        // À définir dans gradle.properties : cyberagenda.proposeEmail=adresse@exemple.org
        val email = (project.findProperty("cyberagenda.proposeEmail") as String?).orEmpty()
        require(email.isEmpty() || Regex("[^@\\s\"]+@[^@\\s\"]+\\.[a-zA-Z]{2,}").matches(email)) { "cyberagenda.proposeEmail invalide" }
        buildConfigField("String", "PROPOSE_EMAIL", "\"$email\"")
        // Dépôt GitHub qui reçoit les propositions (issue pré-remplie), propriétaire/nom.
        val repo = (project.findProperty("cyberagenda.proposeRepo") as String?) ?: "kalypt-orga/CyberAgenda"
        require(Regex("[A-Za-z0-9-]{1,39}/[A-Za-z0-9._-]{1,100}").matches(repo)) { "cyberagenda.proposeRepo invalide" }
        buildConfigField("String", "PROPOSE_REPO", "\"$repo\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":core:network"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
}
