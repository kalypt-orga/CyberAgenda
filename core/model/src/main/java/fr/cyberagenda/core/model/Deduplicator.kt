package fr.cyberagenda.core.model

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId

/**
 * Fusionne un même événement publié par plusieurs sources (ex. « SSTIC » et « SSTIC 2027 »).
 * Deux événements sont considérés identiques s'ils commencent le même jour, ont des formats
 * compatibles, et que les mots significatifs du titre le plus court se retrouvent presque
 * tous dans l'autre, sans que l'autre en ajoute beaucoup.
 *
 * Garde-fous contre une source qui voudrait masquer ou remplacer des événements :
 * - pas de titre « fourre-tout » : l'écart de mots significatifs est plafonné ;
 * - pas de fusion en chaîne : un événement doit ressembler à TOUS les membres du groupe ;
 * - un groupe contient au plus un événement par source (une source ne masque jamais les siens) ;
 * - l'événement affiché garde son propre lien ; il ne reprend des coordonnées que d'une
 *   source de confiance (priorité ≤ [TRUSTED_PRIORITY]).
 */
object Deduplicator {
    private val stopWords = setOf(
        "the", "and", "des", "les", "une", "pour", "sur", "par", "avec", "edition", "conference",
        "conf", "summit", "forum", "europe", "france", "ctf",
    )

    fun merge(events: List<Event>, sourcePriority: (String) -> Int, zone: ZoneId = ZoneId.systemDefault()): List<Event> {
        val byDay = events.groupBy { Instant.ofEpochMilli(it.startsAtEpochMs).atZone(zone).toLocalDate() }
        return byDay.values.flatMap { sameDay ->
            val groups = mutableListOf<MutableList<Event>>()
            for (e in sameDay) {
                val group = groups.firstOrNull { g -> g.none { it.sourceId == e.sourceId } && g.all { sameEvent(it, e) } }
                if (group != null) group += e else groups += mutableListOf(e)
            }
            groups.map { g ->
                // L'événement conservé : source prioritaire, puis le plus complet.
                val primary = g.sortedWith(
                    compareBy<Event> { sourcePriority(it.sourceId) }
                        .thenByDescending { (if (it.latitude != null) 1000 else 0) + it.description.length }
                ).first()
                val others = g - primary
                val donor = others.firstOrNull { sourcePriority(it.sourceId) <= TRUSTED_PRIORITY && it.latitude != null }
                primary.copy(
                    latitude = primary.latitude ?: donor?.latitude,
                    longitude = if (primary.latitude != null) primary.longitude else donor?.longitude,
                    beginnerFriendly = g.any { it.beginnerFriendly },
                    duplicateIds = others.map { it.id },
                    duplicateSourceIds = others.map { it.sourceId }.distinct(),
                )
            }
        }.sortedBy { it.startsAtEpochMs }
    }

    fun sameEvent(a: Event, b: Event): Boolean {
        if (!compatibleFormats(a.format, b.format)) return false
        val ta = tokens(a.title)
        val tb = tokens(b.title)
        if (ta.isEmpty() || tb.isEmpty()) return false
        val (small, big) = if (ta.size <= tb.size) ta to tb else tb to ta
        if (big.size > small.size + MAX_EXTRA_WORDS) return false
        return small.count { it in big }.toDouble() / small.size >= 0.8
    }

    /** Un événement en ligne n'est jamais le même qu'un événement uniquement sur place. */
    private fun compatibleFormats(a: EventFormat, b: EventFormat) =
        a == b || a == EventFormat.HYBRID || b == EventFormat.HYBRID

    /** Sources assez fiables pour compléter les coordonnées d'un doublon (sélection, liste tenue à la main). */
    const val TRUSTED_PRIORITY = 1

    /** Nombre maximal de mots significatifs que le titre le plus long peut ajouter. */
    private const val MAX_EXTRA_WORDS = 2

    internal fun tokens(title: String): Set<String> =
        Normalizer.normalize(title.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 && it !in stopWords && !Regex("(19|20)\\d\\d|2k\\d\\d|\\d+").matches(it) }
            .toSet()
}
