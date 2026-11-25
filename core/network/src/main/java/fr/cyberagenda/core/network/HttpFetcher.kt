package fr.cyberagenda.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Point d'accès réseau unique pour les sources. Impose https, limite la taille des
 * réponses (protection contre l'épuisement mémoire) et vérifie le type de contenu.
 */
@Singleton
class HttpFetcher @Inject constructor(private val client: OkHttpClient) {

    suspend fun getText(url: String, maxBytes: Long = DEFAULT_MAX_BYTES, headers: Map<String, String> = emptyMap()): String =
        getBytes(url, maxBytes, headers).toString(Charsets.UTF_8)

    /** Octets bruts : indispensable pour vérifier une signature sur le contenu exact reçu. */
    /** [headers] : en-têtes propres à une source (ex. clé d'API, jamais dans l'URL où elle finirait dans les journaux). */
    suspend fun getBytes(url: String, maxBytes: Long = DEFAULT_MAX_BYTES, headers: Map<String, String> = emptyMap()): ByteArray =
        withContext(Dispatchers.IO) {
            val httpUrl = SafeUrl.parseHttps(url) ?: throw IOException("URL refusée (https obligatoire)")
            val request = Request.Builder().url(httpUrl).get()
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw HttpStatusException(response.code)
                val body = response.body
                val declared = body.contentLength()
                if (declared > maxBytes) throw ResponseTooLargeException()
                val source = body.source()
                // Lecture bornée : on ne fait pas confiance au Content-Length annoncé.
                if (source.request(maxBytes + 1)) throw ResponseTooLargeException()
                source.buffer.readByteArray()
            }
        }

    companion object {
        const val DEFAULT_MAX_BYTES = 2L * 1024 * 1024
    }
}

class HttpStatusException(val code: Int) : IOException("HTTP $code")
class ResponseTooLargeException : IOException("Réponse trop volumineuse")
