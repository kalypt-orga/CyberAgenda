package fr.cyberagenda.core.model

import java.text.Normalizer
import java.util.Locale

/** Zone récemment choisie sur la carte : la ville et le rayon utilisé. */
data class RecentPlace(
    val name: String,
    val department: String,
    val latitude: Double,
    val longitude: Double,
    val radiusKm: Int,
) {
    /** Même ville, quel que soit le rayon. */
    val key: String get() = RecentSearches.key(name) + "|" + department
}

/**
 * Règles communes des historiques de recherche : au plus [MAX_STORED] entrées, la plus récente
 * en tête, sans doublon (« Paris » et « paris » ne font qu'une entrée), [SHOWN] proposées.
 */
object RecentSearches {
    const val MAX_STORED = 10
    const val SHOWN = 3
    const val MAX_QUERY = 60

    /** Clé de comparaison : minuscules, sans accents ni espaces superflus. */
    fun key(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Ajoute (ou remonte) [item] en tête, sans doublon, en gardant [max] entrées. */
    fun <T> push(list: List<T>, item: T, key: (T) -> String, max: Int = MAX_STORED): List<T> =
        (listOf(item) + list.filter { key(it) != key(item) }).take(max)

    /** Recherche texte à mémoriser, ou null si elle ne vaut pas la peine (trop courte). */
    fun cleanQuery(raw: String): String? =
        TextSanitizer.clean(raw.replace('\n', ' ').replace('\t', ' '), MAX_QUERY).takeIf { key(it).length >= 2 }

    /** Entrées proposées : les plus récentes si rien n'est tapé, sinon celles qui commencent pareil. */
    fun <T> suggestions(list: List<T>, typed: String, label: (T) -> String): List<T> {
        val k = key(typed)
        return (if (k.isEmpty()) list else list.filter { key(label(it)).startsWith(k) && key(label(it)) != k }).take(SHOWN)
    }
}
