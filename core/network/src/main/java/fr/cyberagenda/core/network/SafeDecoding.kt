package fr.cyberagenda.core.network

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.DateTimeException

/**
 * Décode une réponse JSON de type liste élément par élément : une entrée invalide (champ
 * obligatoire à null, nombre hors limites…) est ignorée au lieu de faire échouer toute la
 * réponse. Une réponse qui n'est pas une liste JSON lève toujours [SerializationException].
 */
inline fun <reified T> Json.decodeEach(body: String): List<T> =
    decodeFromString(JsonArray.serializer(), body).mapNotNull { element ->
        try {
            decodeFromJsonElement<T>(element)
        } catch (_: IllegalArgumentException) { // inclut SerializationException
            null
        }
    }

/**
 * Convertit chaque élément ; un élément aux dates aberrantes (année hors limites, débordement)
 * est ignoré au lieu de faire échouer toute la source.
 */
inline fun <A, B : Any> List<A>.mapEachSafely(transform: (A) -> B?): List<B> = mapNotNull {
    try {
        transform(it)
    } catch (_: DateTimeException) {
        null
    } catch (_: ArithmeticException) {
        null
    }
}
