package fr.cyberagenda.core.model

/** Description d'une source d'événements, affichée dans le menu « Sources ». */
data class SourceMeta(
    val id: String,
    val name: String,
    val description: String,
    /** Site de la source, pour que l'utilisateur sache d'où viennent les données. */
    val homepage: String,
    val kinds: Set<EventKind>,
    /** true si les données sont signées et vérifiées cryptographiquement par l'app. */
    val verified: Boolean = false,
    /** Rang d'affichage dans les paramètres : poids de la source pour un public de niveau moyen en cyber. */
    val order: Int = 100,
    /** false = source en travaux : affichée mais impossible à activer, jamais interrogée. */
    val available: Boolean = true,
)

/** État d'une source tel que présenté à l'utilisateur. */
data class SourceState(
    val meta: SourceMeta,
    val enabled: Boolean,
    val lastSuccessEpochMs: Long?,
    val lastError: SourceError?,
    val eventCount: Int,
)

enum class SourceError { NETWORK, INVALID_DATA, SIGNATURE, UNKNOWN }
