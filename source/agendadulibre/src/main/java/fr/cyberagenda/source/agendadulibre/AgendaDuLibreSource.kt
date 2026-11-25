package fr.cyberagenda.source.agendadulibre

import dagger.Binds
import fr.cyberagenda.core.network.mapEachSafely
import fr.cyberagenda.core.network.decodeEach
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import fr.cyberagenda.core.data.EventSource
import fr.cyberagenda.core.data.InvalidSourceDataException
import fr.cyberagenda.core.formats.HtmlToText
import fr.cyberagenda.core.model.Event
import fr.cyberagenda.core.model.EventFormat
import fr.cyberagenda.core.model.EventKind
import fr.cyberagenda.core.model.SourceMeta
import fr.cyberagenda.core.network.HttpFetcher
import fr.cyberagenda.core.network.SafeUrl
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.inject.Inject

@Serializable
internal data class AdlEventDto(
    val id: Long,
    val title: String,
    val description: String = "",
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("place_name") val placeName: String? = null,
    val address: String? = null,
    val city: String? = null,
    val url: String? = null,
    @SerialName("region_id") val regionId: Int? = null,
    val region: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val tags: String = "",
)

/**
 * Agenda du Libre : ateliers, cafés vie privée, permanences… ouverts au grand public.
 * On interroge l'API publique par mots-clés liés à la sécurité et à la vie privée.
 */
class AgendaDuLibreSource @Inject constructor(
    private val fetcher: HttpFetcher,
    private val json: Json,
) : EventSource {

    override val meta = SourceMeta(
        id = ID,
        name = "Agenda du Libre",
        description = "Ateliers et cafés « vie privée », chiffrement, sécurité de ses appareils. " +
            "Ouverts à tous, sans prérequis : idéal pour débuter.",
        homepage = "https://www.agendadulibre.org",
        kinds = setOf(EventKind.WORKSHOP, EventKind.MEETUP),
        order = 3,
    )

    override suspend fun fetch(): List<Event> = coroutineScope {
        val results = TAGS.map { tag -> async { fetchTag(tag) } }.awaitAll()
        // Si toutes les requêtes ont échoué, on remonte l'erreur ; sinon on garde ce qui a marché.
        if (results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
        results.flatMap { it.getOrDefault(emptyList()) }
            .distinctBy { it.id }
            .mapEachSafely { AgendaDuLibreMapper.map(it, ID) }
    }

    private suspend fun fetchTag(tag: String): Result<List<AdlEventDto>> = try {
        val url = HttpUrl.Builder().scheme("https").host(HOST)
            .addPathSegment("events.json").addQueryParameter("tag", tag).build()
        Result.success(json.decodeEach<AdlEventDto>(fetcher.getText(url.toString())))
    } catch (e: IOException) {
        Result.failure(e)
    } catch (e: SerializationException) {
        Result.failure(InvalidSourceDataException("Réponse Agenda du Libre illisible", e))
    } catch (e: IllegalArgumentException) {
        Result.failure(InvalidSourceDataException("Réponse Agenda du Libre illisible", e))
    }

    companion object {
        const val ID = "agendadulibre"
        private const val HOST = "www.agendadulibre.org"
        private val TAGS = listOf(
            "sécurité", "cybersécurité", "chiffrement", "vie-privée", "respect-vie-privée",
            "café-vie-privée", "cryptoparty", "autodéfense-numérique", "anonymat", "données-personnelles",
        )
    }
}

internal object AgendaDuLibreMapper {
    /** Régions françaises (métropole + outre-mer) dans le référentiel de l'Agenda du Libre. */
    private val franceRegionIds = setOf(1, 2, 3, 4, 5, 6, 7, 9, 12, 13, 17, 18, 21, 23, 24, 25, 26, 28, 29, 30)

    fun map(dto: AdlEventDto, sourceId: String): Event? {
        val online = dto.region.equals("Internet", ignoreCase = true)
        if (!online && dto.regionId !in franceRegionIds) return null

        val start = parse(dto.startTime) ?: return null
        val end = parse(dto.endTime) ?: return null
        val url = SafeUrl.parseHttps(dto.url)?.toString() ?: "https://www.agendadulibre.org/events/${dto.id}"
        val title = dto.title
        val kind = when {
            title.contains("conférence", true) -> EventKind.CONFERENCE
            title.contains("café", true) || title.contains("apéro", true) -> EventKind.MEETUP
            else -> EventKind.WORKSHOP
        }
        return Event(
            id = "$sourceId:${dto.id}",
            sourceId = sourceId,
            title = title,
            description = HtmlToText.convert(dto.description),
            startsAtEpochMs = start,
            endsAtEpochMs = end,
            format = if (online) EventFormat.ONLINE else EventFormat.ONSITE,
            kind = kind,
            url = url,
            // Adresse complète : utile pour l'itinéraire (l'app de cartographie la géolocalise).
            location = if (online) null else listOfNotNull(dto.placeName, dto.address, dto.city)
                .filter { it.isNotBlank() }.joinToString(", ").ifBlank { null },
            latitude = if (online) null else dto.latitude,
            longitude = if (online) null else dto.longitude,
            // Événements grand public par nature (ateliers, permanences, cafés).
            beginnerFriendly = true,
            free = null,
        )
    }

    private fun parse(raw: String): Long? = try {
        Instant.parse(raw).toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AgendaDuLibreModule {
    @Binds @IntoSet
    abstract fun bindSource(source: AgendaDuLibreSource): EventSource
}
