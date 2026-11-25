package fr.cyberagenda.source.ctftime

import fr.cyberagenda.core.model.Event
import fr.cyberagenda.core.formats.HtmlToText
import fr.cyberagenda.core.formats.MarkdownText
import fr.cyberagenda.core.model.EventFormat
import fr.cyberagenda.core.model.EventKind
import fr.cyberagenda.core.network.SafeUrl
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

internal object CtfTimeMapper {
    /** Inscriptions inaccessibles à un débutant (sur qualification / invitation). */
    private val closedRestrictions = setOf("prequalified", "invited")

    private val franceLocation = Regex(
        "\\b(france|paris|lyon|marseille|toulouse|lille|rennes|bordeaux|nantes|strasbourg|" +
            "montpellier|nice|grenoble|rouen|brest|caen|nancy|metz|dijon|tours|angers|orl[eé]ans)\\b",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Conserve les CTF en ligne (accessibles depuis la France) et les CTF sur place situés
     * en France. Renvoie null pour un événement hors périmètre ou mal formé.
     */
    fun map(dto: CtfTimeEventDto, sourceId: String): Event? {
        if (dto.restrictions.lowercase() in closedRestrictions) return null
        if (dto.onsite && !franceLocation.containsMatchIn(dto.location)) return null

        val start = parseInstant(dto.start) ?: return null
        val end = parseInstant(dto.finish) ?: return null
        // Lien officiel du CTF si valide, sinon la page CTFtime.
        val url = SafeUrl.parseHttps(dto.url)?.toString()
            ?: SafeUrl.parseHttps(dto.ctftimeUrl)?.toString()
            ?: return null

        val description = buildString {
            if (dto.format.isNotBlank()) append("Format : ").append(dto.format).append("\n\n")
            // Les organisateurs peuvent y mettre du HTML (liens, gras) : converti en texte brut.
            append(MarkdownText.plain(HtmlToText.convert(dto.description)))
        }

        return Event(
            id = "$sourceId:${dto.id}",
            sourceId = sourceId,
            title = dto.title,
            description = description,
            startsAtEpochMs = start,
            endsAtEpochMs = end,
            format = if (dto.onsite) EventFormat.ONSITE else EventFormat.ONLINE,
            kind = EventKind.CTF,
            url = url,
            organizer = dto.organizers.firstOrNull()?.name?.takeIf { it.isNotBlank() },
            location = dto.location.takeIf { dto.onsite && it.isNotBlank() },
            beginnerFriendly = false, // CTFtime ne fournit pas cette information
            free = null,
        )
    }

    private fun parseInstant(raw: String): Long? = try {
        OffsetDateTime.parse(raw).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}
