package fr.cyberagenda.core.data

import fr.cyberagenda.core.model.Event
import fr.cyberagenda.core.model.SourceMeta

/**
 * Contrat d'une source d'événements. Pour ajouter une source :
 *  1. créer un module :source:xxx qui implémente cette interface ;
 *  2. l'enregistrer dans Hilt avec `@Binds @IntoSet` ;
 *  3. déclarer son domaine (et ses pins) dans network_security_config.xml ;
 *  4. ajouter le module aux dépendances de :app.
 * Elle apparaît alors automatiquement dans le menu « Sources ».
 */
interface EventSource {
    val meta: SourceMeta

    /**
     * Récupère les événements à venir. Les identifiants doivent être préfixés par
     * "${meta.id}:". Les résultats sont de toute façon revalidés par le dépôt.
     * Lever [InvalidSourceDataException] si les données sont incohérentes,
     * [SignatureException] si une signature est invalide.
     */
    suspend fun fetch(): List<Event>

    /**
     * Intervalle minimal entre deux synchronisations automatiques, si la source l'exige (quota
     * d'API). null = intervalle commun de l'app.
     */
    val refreshIntervalMs: Long? get() = null

    /** Délai minimal même pour une actualisation demandée à la main (0 = aucun). */
    val forcedRefreshFloorMs: Long get() = 0
}

class InvalidSourceDataException(message: String, cause: Throwable? = null) : Exception(message, cause)
class SignatureException(message: String) : Exception(message)
