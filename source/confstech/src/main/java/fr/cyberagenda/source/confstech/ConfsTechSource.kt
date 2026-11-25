package fr.cyberagenda.source.confstech

import dagger.Binds
import fr.cyberagenda.core.network.mapEachSafely
import fr.cyberagenda.core.network.decodeEach
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import fr.cyberagenda.core.data.EventSource
import fr.cyberagenda.core.data.InvalidSourceDataException
import fr.cyberagenda.core.model.Event
import fr.cyberagenda.core.model.EventFormat
import fr.cyberagenda.core.model.EventKind
import fr.cyberagenda.core.model.SourceMeta
import fr.cyberagenda.core.network.HttpFetcher
import fr.cyberagenda.core.network.HttpStatusException
import fr.cyberagenda.core.network.SafeUrl
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.Year
import java.time.ZoneId
import java.time.format.DateTimeParseException
import javax.inject.Inject

@Serializable
internal data class ConfDto(
    val name: String,
    val url: String = "",
    val startDate: String,
    val endDate: String? = null,
    val city: String? = null,
    val country: String? = null,
    val online: Boolean = false,
)

/** Données ouvertes de confs.tech (GitHub) : conférences « security », en ligne ou en France. */
class ConfsTechSource @Inject constructor(
    private val fetcher: HttpFetcher,
    private val json: Json,
) : EventSource {

    override val meta = SourceMeta(
        id = ID,
        name = "confs.tech",
        description = "Conférences sécurité internationales accessibles en ligne, et celles organisées en France. " +
            "Données ouvertes et communautaires.",
        homepage = "https://confs.tech/security",
        kinds = setOf(EventKind.CONFERENCE),
        order = 5,
    )

    override suspend fun fetch(): List<Event> {
        val year = Year.now().value
        return listOf(year, year + 1).flatMap { y ->
            val body = try {
                fetcher.getText("https://raw.githubusercontent.com/tech-conferences/conference-data/main/conferences/$y/security.json")
            } catch (e: HttpStatusException) {
                if (e.code == 404 && y != year) return@flatMap emptyList() // année suivante pas encore publiée
                throw e
            }
            val dtos = try {
                json.decodeEach<ConfDto>(body)
            } catch (e: SerializationException) {
                throw InvalidSourceDataException("Réponse confs.tech illisible", e)
            } catch (e: IllegalArgumentException) {
                throw InvalidSourceDataException("Réponse confs.tech illisible", e)
            }
            dtos.mapEachSafely { ConfsTechMapper.map(it, ID) }
        }
    }

    companion object { const val ID = "confstech" }
}

internal object ConfsTechMapper {
    private val zone = ZoneId.of("Europe/Paris")
    private val slug = Regex("[^a-z0-9]+")
    private val securityName = Regex(
        "secur|s[ée]curit|sec(?![a-z])|cyber|hack|\\bctf\\b|owasp|privacy|crypt|bsides|def ?con|pentest|" +
            "red ?team|blue ?team|threat|malware|forensic|zero ?trust|\\bsoc\\b",
        RegexOption.IGNORE_CASE,
    )

    fun map(dto: ConfDto, sourceId: String): Event? {
        val inFrance = dto.country.equals("France", ignoreCase = true)
        if (!inFrance && !dto.online) return null
        // La liste « security » de confs.tech contient aussi des conférences API, IA ou dev
        // simplement dotées d'une piste sécurité : on ne garde que celles dont le nom parle de sécurité.
        if (!securityName.containsMatchIn(dto.name)) return null
        val url = SafeUrl.parseHttps(dto.url)?.toString() ?: return null
        val start = date(dto.startDate) ?: return null
        val end = date(dto.endDate ?: dto.startDate) ?: start
        return Event(
            id = "$sourceId:" + (dto.name.lowercase().replace(slug, "-").trim('-').take(80) + "-" + dto.startDate),
            sourceId = sourceId,
            title = dto.name,
            description = buildString {
                append("Conférence sécurité")
                if (dto.online) append(" accessible en ligne")
                if (!dto.city.isNullOrBlank()) append(" · ").append(dto.city)
                if (!dto.country.isNullOrBlank()) append(", ").append(dto.country)
                if (!endsWith(".")) append(".")
                append("\nLes conférences internationales sont souvent en anglais.")
            },
            startsAtEpochMs = start.atStartOfDay(zone).toInstant().toEpochMilli(),
            endsAtEpochMs = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1,
            format = when {
                inFrance && dto.online -> EventFormat.HYBRID
                inFrance -> EventFormat.ONSITE
                else -> EventFormat.ONLINE
            },
            kind = EventKind.CONFERENCE,
            url = url,
            location = if (inFrance) dto.city else null,
        )
    }

    private fun date(raw: String): LocalDate? = try { LocalDate.parse(raw) } catch (_: DateTimeParseException) { null }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ConfsTechModule {
    @Binds @IntoSet
    abstract fun bindSource(source: ConfsTechSource): EventSource
}
