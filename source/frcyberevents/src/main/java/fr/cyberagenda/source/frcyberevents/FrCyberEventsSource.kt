package fr.cyberagenda.source.frcyberevents

import dagger.Binds
import fr.cyberagenda.core.network.mapEachSafely
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import fr.cyberagenda.core.data.EventSource
import fr.cyberagenda.core.formats.IcsEvent
import fr.cyberagenda.core.formats.IcsParser
import fr.cyberagenda.core.model.Event
import fr.cyberagenda.core.model.EventFormat
import fr.cyberagenda.core.model.EventKind
import fr.cyberagenda.core.model.SourceMeta
import fr.cyberagenda.core.network.HttpFetcher
import fr.cyberagenda.core.network.SafeUrl
import javax.inject.Inject

/**
 * Calendrier communautaire des événements cyber en France (salons, conférences, CTF),
 * maintenu sur GitHub par Alexandre Dubois, publié au format iCalendar.
 */
class FrCyberEventsSource @Inject constructor(private val fetcher: HttpFetcher) : EventSource {

    override val meta = SourceMeta(
        id = ID,
        name = "Cybersecurity Events FR",
        description = "Grands rendez-vous cyber en France : salons, conférences, forums et CTF. " +
            "Liste communautaire maintenue sur GitHub.",
        homepage = "https://github.com/alexandredubois/cybersecurity-events",
        kinds = setOf(EventKind.CONFERENCE, EventKind.CTF),
        order = 2,
    )

    override suspend fun fetch(): List<Event> =
        IcsParser.parse(fetcher.getText(URL)).mapEachSafely { FrCyberEventsMapper.map(it, ID) }

    companion object {
        const val ID = "frcyberevents"
        private const val URL = "https://alexandredubois.github.io/cybersecurity-events/calendar.ics"
    }
}

internal object FrCyberEventsMapper {
    private val safeId = Regex("[^A-Za-z0-9._-]")
    private const val FALLBACK_URL = "https://github.com/alexandredubois/cybersecurity-events"

    private val formats = mapOf(
        "conferences" to "conférences", "conference" to "conférence", "exhibitors" to "exposants",
        "workshops" to "ateliers", "workshop" to "atelier", "ctf" to "CTF", "talks" to "talks", "trainings" to "formations",
    )

    /**
     * Description du calendrier : « Site de l'évènement : … » puis une ligne de formats en anglais
     * (« Conferences, Exhibitors ») et un éventuel thème. Lignes nettoyées, formats traduits.
     */
    internal fun describe(raw: String): String = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
        val parts = line.split(",").map { it.trim().lowercase() }
        if (parts.all { it in formats }) "Au programme : " + parts.joinToString(", ") { formats.getValue(it) } + "."
        else line
    }.joinToString("\n")

    fun map(ics: IcsEvent, sourceId: String): Event? {
        val uid = ics.uid.replace(safeId, "").take(100).ifEmpty { return null }
        val online = Regex("en ligne|online|virtuel", RegexOption.IGNORE_CASE).containsMatchIn(ics.location)
        val types = ics.description.lowercase()
        return Event(
            id = "$sourceId:$uid",
            sourceId = sourceId,
            title = ics.summary,
            description = describe(ics.description),
            startsAtEpochMs = ics.startEpochMs,
            endsAtEpochMs = ics.endEpochMs,
            format = if (online) EventFormat.ONLINE else EventFormat.ONSITE,
            kind = when {
                ics.summary.contains("CTF", true) -> EventKind.CTF
                "conference" in types || "conférence" in types -> EventKind.CONFERENCE
                else -> EventKind.OTHER
            },
            url = SafeUrl.parseHttps(ics.url)?.toString() ?: FALLBACK_URL,
            location = ics.location.takeIf { !online && it.isNotBlank() },
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FrCyberEventsModule {
    @Binds @IntoSet
    abstract fun bindSource(source: FrCyberEventsSource): EventSource
}
