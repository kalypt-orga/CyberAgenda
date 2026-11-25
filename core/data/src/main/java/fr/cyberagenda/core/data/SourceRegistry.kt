package fr.cyberagenda.core.data

import javax.inject.Inject
import javax.inject.Singleton

/** Catalogue fermé des sources intégrées à l'app (l'utilisateur ne peut pas ajouter d'URL arbitraire). */
@Singleton
class SourceRegistry @Inject constructor(sources: Set<@JvmSuppressWildcards EventSource>) {
    /** Sources dans l'ordre d'affichage (rang choisi, puis nom). */
    val all: List<EventSource> = sources.sortedWith(compareBy({ it.meta.order }, { it.meta.name }))

    init {
        val ids = all.map { it.meta.id }
        require(ids.size == ids.toSet().size) { "Identifiants de sources en double : $ids" }
        require(ids.all { ID_PATTERN.matches(it) }) { "Identifiant de source invalide : $ids" }
    }

    operator fun get(id: String): EventSource? = all.firstOrNull { it.meta.id == id }

    private companion object {
        val ID_PATTERN = Regex("[a-z0-9-]{2,32}")
    }
}
