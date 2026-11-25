package fr.cyberagenda.core.model

/**
 * Thème de l'application. Après [SYSTEM] : les thèmes secrets, débloqués en touchant 7 fois la
 * version dans les paramètres (une valeur inconnue enregistrée revient à [SYSTEM]).
 */
enum class ThemeMode { LIGHT, DARK, SYSTEM, BRUTAL, NOTEBOOK, ARCADE, BLUEPRINT, GAZETTE, ITASHA }

/** Moment du rappel pour les événements mis en favori. */
enum class ReminderMode(val beforeMs: Long) {
    OFF(0),
    DAY_BEFORE(24L * 60 * 60 * 1000),
    HOUR_BEFORE(60L * 60 * 1000),
}
