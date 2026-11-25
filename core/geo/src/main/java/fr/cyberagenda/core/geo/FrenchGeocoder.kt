package fr.cyberagenda.core.geo

import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

data class GeoPoint(val latitude: Double, val longitude: Double) {
    /** Distance à vol d'oiseau en kilomètres (formule de haversine). */
    fun distanceKm(other: GeoPoint): Double {
        val r = 6371.0
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLon = Math.toRadians(other.longitude - longitude)
        val a = Math.sin(dLat / 2).let { it * it } +
            Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(other.latitude)) * Math.sin(dLon / 2).let { it * it }
        return 2 * r * Math.asin(Math.sqrt(a))
    }
}

/** Ville proposée lors d'une recherche. */
data class City(val name: String, val department: String, val point: GeoPoint)

/**
 * Géocodage 100 % hors ligne à partir d'une table embarquée des communes françaises
 * (geo.api.gouv.fr, Licence Ouverte). Aucun lieu n'est envoyé à un service tiers.
 */
@Singleton
class FrenchGeocoder internal constructor(private val loadTable: () -> String) {

    @Inject constructor(@ApplicationContext context: Context) : this({
        context.assets.open("communes.tsv").bufferedReader().use { it.readText() }
    })

    /** [key] : nom normalisé, calculé une seule fois au chargement (≈ 35 000 communes). */
    private data class Place(val name: String, val key: String, val department: String, val point: GeoPoint, val population: Int)

    /** Toutes les communes, les plus peuplées d'abord (la recherche s'arrête dès qu'elle a assez de résultats). */
    private val places: List<Place> by lazy {
        loadTable().lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val c = line.split('\t')
                if (c.size < 5) return@mapNotNull null
                val lat = c[2].toDoubleOrNull() ?: return@mapNotNull null
                val lon = c[3].toDoubleOrNull() ?: return@mapNotNull null
                // 6e colonne : nom déjà normalisé à la génération (évite ~35 000 normalisations au démarrage).
                val key = c.getOrNull(5)?.takeIf { it.isNotEmpty() } ?: normalize(c[0])
                Place(c[0], key, c[1], GeoPoint(lat, lon), c[4].toIntOrNull() ?: 0)
            }
            .sortedByDescending { it.population }
            .toList()
    }

    private val byName: Map<String, List<Place>> by lazy { places.groupBy { it.key } }

    private val loaded = MutableStateFlow(false)

    /** Vrai une fois la table chargée : les libellés courts peuvent alors être calculés sans attente. */
    val ready: StateFlow<Boolean> = loaded.asStateFlow()

    /** Charge la table à l'avance (hors du thread de l'interface) : la première recherche est immédiate. */
    fun warmUp() { places.size; byName.size; loaded.value = true }
    private val biggestByDepartment: Map<String, Place> by lazy {
        places.groupBy { it.department }.mapValues { (_, v) -> v.maxBy { it.population } }
    }

    /**
     * Recherche de villes par préfixe (insensible aux accents et à la casse), les plus peuplées
     * d'abord. Entièrement locale : la saisie de l'utilisateur ne quitte jamais l'appareil.
     */
    fun search(query: String, limit: Int = 6): List<City> {
        val q = normalize(query.take(60))
        if (q.length < 2) return emptyList()
        return places.asSequence()
            .filter { it.key.startsWith(q) } // déjà triées par population
            .take(limit)
            .map { City(it.name, it.department, it.point) }
            .toList()
    }

    /** Ex. « Couvent des Jacobins, Rennes (35) », « Paris, France », « 92170 Vanves ». */
    fun locate(location: String): GeoPoint? {
        val text = location.take(300)
        return (findPlace(text) ?: departmentOf(text)?.let { biggestByDepartment[it] })?.point
    }

    /**
     * Libellé court d'un lieu pour les miniatures : « (84) Avignon ». La ville est cherchée
     * d'abord dans la dernière partie de l'adresse (« MEETT, Parc des Expositions de Toulouse,
     * Aussonne (31) » → « (31) Aussonne », pas Toulouse), puis dans le texte entier.
     */
    fun shortLabel(location: String): String {
        val text = location.take(300)
        val parts = text.split(',').map { it.trim() }.filter { it.isNotEmpty() && normalize(it) != "france" }
        val last = parts.lastOrNull() ?: return text.trim()
        findPlace(last)?.let { return "(${it.department}) ${it.name}" }
        // Ville absente de la table mais département indiqué : « Ville (31) » → « (31) Ville ».
        val marked = Regex("^(.*?)\\s*\\((2[AB]|97\\d|\\d{2})\\)$").find(last)
        if (marked != null && marked.groupValues[1].isNotBlank()) return "(${marked.groupValues[2]}) ${marked.groupValues[1].trim()}"
        return findPlace(text)?.let { "(${it.department}) ${it.name}" } ?: last
    }

    private fun findPlace(text: String): Place? {
        val department = departmentOf(text)
        val tokens = normalize(text).split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null

        var best: Place? = null
        var bestLen = 0
        for (len in minOf(5, tokens.size) downTo 1) {
            for (i in 0..tokens.size - len) {
                // Un mot isolé de moins de 3 lettres (« F » de « Station F ») n'est pas une ville.
                if (len == 1 && tokens[i].length < 3) continue
                val candidates = byName[tokens.subList(i, i + len).joinToString(" ")] ?: continue
                val match = candidates
                    .filter { department == null || it.department == department }
                    .maxByOrNull { it.population } ?: continue
                if (len > bestLen || (len == bestLen && match.population > (best?.population ?: 0))) {
                    best = match; bestLen = len
                }
            }
            if (best != null) break
        }
        return best
    }

    private fun departmentOf(text: String): String? {
        Regex("\\((2[AB]|97\\d|\\d{2})\\)").find(text)?.let { return it.groupValues[1] }
        Regex("\\b(97\\d)\\d{2}\\b").find(text)?.let { return it.groupValues[1] }
        Regex("\\b(\\d{2})\\d{3}\\b").find(text)?.let { return it.groupValues[1].takeIf { d -> d != "20" } }
        return null
    }

    internal companion object {
        fun normalize(s: String): String =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()
    }
}
