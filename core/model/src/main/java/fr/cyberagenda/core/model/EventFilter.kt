package fr.cyberagenda.core.model

/** Niveau : tout afficher, seulement les événements accessibles aux débutants, ou les masquer. */
enum class BeginnerFilter { ALL, ONLY, HIDE }

/**
 * Filtre commun à l'agenda, au calendrier et à la carte. Il est enregistré sur le téléphone :
 * il reste actif d'un onglet à l'autre et après la fermeture de l'application.
 * Un ensemble vide signifie « tous ».
 */
data class EventFilter(
    val formats: Set<EventFormat> = emptySet(),
    val kinds: Set<EventKind> = emptySet(),
    val beginner: BeginnerFilter = BeginnerFilter.ALL,
    val favoritesOnly: Boolean = false,
    /** Inclure les événements terminés depuis moins de 3 mois (agenda, calendrier, carte). */
    val includePast: Boolean = false,
) {
    val activeCount: Int
        get() = (if (formats.isNotEmpty()) 1 else 0) + (if (kinds.isNotEmpty()) 1 else 0) +
            (if (beginner != BeginnerFilter.ALL) 1 else 0) + (if (favoritesOnly) 1 else 0) + (if (includePast) 1 else 0)

    val isDefault: Boolean get() = activeCount == 0

    fun matches(event: Event, favorite: Boolean): Boolean {
        if (favoritesOnly && !favorite) return false
        when (beginner) {
            BeginnerFilter.ONLY -> if (!event.beginnerFriendly) return false
            BeginnerFilter.HIDE -> if (event.beginnerFriendly) return false
            BeginnerFilter.ALL -> Unit
        }
        if (kinds.isNotEmpty() && event.kind !in kinds) return false
        if (formats.isNotEmpty()) {
            // Un événement hybride correspond à « en ligne » comme à « sur place ».
            val ok = event.format in formats || (event.format == EventFormat.HYBRID && formats.isNotEmpty())
            if (!ok) return false
        }
        return true
    }
}
