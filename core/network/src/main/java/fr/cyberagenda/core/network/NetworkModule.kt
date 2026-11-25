package fr.cyberagenda.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        // TLS moderne uniquement (TLS 1.2+ avec suites robustes), jamais de clair.
        .connectionSpecs(listOf(ConnectionSpec.RESTRICTED_TLS))
        // Aucune redirection suivie : les adresses des sources sont fixes, et une redirection
        // vers un autre domaine sortirait de l'épinglage des certificats.
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    // User-Agent générique : n'expose ni l'appareil ni l'utilisateur.
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build()
            )
        }
        // Pas de cache disque HTTP ni de cookies pour les sources. (Le fond de carte MapLibre a
        // son propre client et son propre cache de tuiles : voir l'écran Confidentialité.)
        .build()

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true // les sources peuvent ajouter des champs
        isLenient = false
        coerceInputValues = true
        explicitNulls = false
    }

    private const val USER_AGENT = "CyberAgenda/0.1 (Android)"
}
