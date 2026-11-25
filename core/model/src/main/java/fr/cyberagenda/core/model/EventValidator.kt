package fr.cyberagenda.core.model

import java.time.Instant
import java.time.ZoneId

/**
 * Contrôle commun appliqué à TOUS les événements, quelle que soit leur source
 * (défense en profondeur : une source compromise ou boguée ne peut pas injecter
 * n'importe quoi dans l'app).
 */
object EventValidator {
    const val MAX_TITLE = 200
    const val MAX_DESCRIPTION = 5_000
    const val MAX_SHORT_FIELD = 200
    const val MAX_URL = 2_048
    private const val DAY_MS = 24L * 60 * 60 * 1000
    /** Un événement ne dure pas plus de 60 jours. */
    private const val MAX_DURATION_MS = 60 * DAY_MS
    /** Fenêtre de dates acceptée autour de maintenant (le passé gardé est de 3 mois). */
    private const val MAX_PAST_MS = 400 * DAY_MS
    private const val MAX_FUTURE_MS = 5 * 366 * DAY_MS

    /** Renvoie l'événement assaini, ou null s'il doit être rejeté. */
    fun sanitize(
        event: Event,
        isHttpsUrl: (String) -> Boolean,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Event? {
        val title = TextSanitizer.clean(event.title, MAX_TITLE)
        // Un titre doit contenir au moins une lettre ou un chiffre (pas de titre « invisible »).
        if (title.none { it.isLetterOrDigit() }) return null
        if (event.id.isBlank() || event.id.length > MAX_SHORT_FIELD) return null
        if (!event.id.startsWith(event.sourceId + ":")) return null
        if (event.url.length > MAX_URL || !isHttpsUrl(event.url)) return null
        // Dates bornées AVANT tout calcul : la soustraction ci-dessous ne peut plus déborder.
        if (event.startsAtEpochMs < nowMs - MAX_PAST_MS || event.startsAtEpochMs > nowMs + MAX_FUTURE_MS) return null
        if (event.endsAtEpochMs < event.startsAtEpochMs) return null
        if (event.endsAtEpochMs - event.startsAtEpochMs > MAX_DURATION_MS) return null
        val endsAt = completeEnd(event.startsAtEpochMs, event.endsAtEpochMs, zone)

        val lat = event.latitude
        val lon = event.longitude
        val coordsValid = lat != null && lon != null &&
            lat in -90.0..90.0 && lon in -180.0..180.0 && !lat.isNaN() && !lon.isNaN()

        return event.copy(
            title = title,
            description = TextSanitizer.clean(event.description, MAX_DESCRIPTION),
            organizer = event.organizer?.let { TextSanitizer.clean(it, MAX_SHORT_FIELD) }?.ifBlank { null },
            location = event.location?.let { TextSanitizer.clean(it, MAX_SHORT_FIELD) }?.ifBlank { null },
            price = event.price?.let { TextSanitizer.clean(it, 60) }?.ifBlank { null },
            endsAtEpochMs = endsAt,
            latitude = if (coordsValid) lat else null,
            longitude = if (coordsValid) lon else null,
        )
    }

    /**
     * Fin manquante : beaucoup de sources ne donnent qu'un début (iCal sans DTEND, début à
     * 00:00 pour « horaire inconnu »). Un événement de durée nulle qui commence à minuit
     * devient la journée entière (fin à 23:59:59.999, convention de l'app) ; à une autre
     * heure, on garde l'instant de début (l'affichage indique alors « à 18:00 »).
     */
    private fun completeEnd(start: Long, end: Long, zone: ZoneId): Long {
        if (end != start) return end
        val t = Instant.ofEpochMilli(start).atZone(zone)
        if (t.hour != 0 || t.minute != 0 || t.second != 0) return end
        return t.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    }
}
