package fr.cyberagenda.core.model

/**
 * Événement normalisé, quelle que soit sa source.
 *
 * Toutes les instances qui atteignent la base de données ont été validées par
 * [EventValidator] : aucun module de source n'est considéré comme digne de confiance.
 */
data class Event(
    /** Identifiant global unique : "<sourceId>:<identifiant dans la source>". */
    val id: String,
    val sourceId: String,
    val title: String,
    val description: String,
    val startsAtEpochMs: Long,
    val endsAtEpochMs: Long,
    val format: EventFormat,
    val kind: EventKind,
    /** URL https d'inscription / d'information, validée. */
    val url: String,
    val organizer: String? = null,
    /** Lieu en texte libre (« Rennes », « Paris - Station F »…), null si en ligne. */
    val location: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val beginnerFriendly: Boolean = false,
    /** null = information inconnue. */
    val free: Boolean? = null,
    /** Tarif en texte libre (« Gratuit », « 20 € », « 50–250 € »), null si inconnu. */
    val price: String? = null,
    /** Même événement publié par d'autres sources (calculé à l'affichage, non stocké). */
    val duplicateIds: List<String> = emptyList(),
    val duplicateSourceIds: List<String> = emptyList(),
    /** Lieu court pour les miniatures, « (84) Avignon » (calculé à l'affichage, non stocké). */
    val placeLabel: String? = null,
) {
    /** Tous les identifiants sous lesquels cet événement existe (pour les favoris). */
    val allIds: List<String> get() = listOf(id) + duplicateIds
}

enum class EventFormat { ONLINE, ONSITE, HYBRID }

enum class EventKind { CTF, CONFERENCE, MEETUP, WORKSHOP, OTHER }
