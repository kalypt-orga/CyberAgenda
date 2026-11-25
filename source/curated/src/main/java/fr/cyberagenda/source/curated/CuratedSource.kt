package fr.cyberagenda.source.curated

import android.content.Context
import fr.cyberagenda.core.network.mapEachSafely
import com.google.crypto.tink.subtle.Ed25519Verify
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import fr.cyberagenda.core.data.EventSource
import fr.cyberagenda.core.data.InvalidSourceDataException
import fr.cyberagenda.core.data.SignatureException
import fr.cyberagenda.core.model.Event
import fr.cyberagenda.core.model.EventFormat
import fr.cyberagenda.core.model.EventKind
import fr.cyberagenda.core.model.SourceMeta
import fr.cyberagenda.core.network.HttpFetcher
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.security.GeneralSecurityException
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.Base64
import javax.inject.Inject

@Serializable
internal data class CuratedFile(val version: Int, val generatedAt: String, val events: List<CuratedEvent>)

@Serializable
internal data class CuratedEvent(
    val id: String,
    val title: String,
    val kind: String = "OTHER",
    val format: String = "ONSITE",
    /** "2027-03-12" (journée entière) ou "2027-03-12T18:30:00+01:00". */
    val start: String,
    val end: String? = null,
    val location: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val url: String,
    val organizer: String? = null,
    val description: String = "",
    val beginnerFriendly: Boolean = false,
    val free: Boolean? = null,
    /** Tarif en clair : « Gratuit », « 20 € », « 50–250 € ». */
    val price: String? = null,
)

/**
 * Sélection d'événements choisis à la main, signée en Ed25519.
 *
 * - Les données embarquées dans l'APK servent de base (fonctionne hors ligne).
 * - Si une URL distante est configurée, la version distante est utilisée seulement si sa
 *   signature est valide ET qu'elle n'est pas plus ancienne que la version embarquée
 *   (protection contre le rejeu d'un ancien fichier signé).
 * - Hébergeur compromis, proxy, fichier modifié : sans la clé privée, rien ne passe.
 */
class CuratedSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fetcher: HttpFetcher,
    private val json: Json,
) : EventSource {

    override val meta = SourceMeta(
        id = ID,
        name = "Sélection CyberAgenda",
        description = "Événements choisis et vérifiés à la main, partout en France. En travaux : bientôt disponible.",
        homepage = BuildConfig.CURATED_URL.ifBlank { "intégrée à l'application" },
        kinds = setOf(EventKind.CONFERENCE, EventKind.CTF, EventKind.MEETUP, EventKind.WORKSHOP),
        verified = true,
        order = 0,
        // En travaux : le mode de publication du flux (dépôt GitHub, site…) reste à choisir.
        available = false,
    )

    override suspend fun fetch(): List<Event> {
        val bundled = verifiedFile(
            context.assets.open(ASSET).use { it.readBytes() },
            context.assets.open("$ASSET.sig").use { it.readBytes() },
        )
        val chosen = remoteOrNull(bundled) ?: bundled
        return chosen.events.mapEachSafely { CuratedMapper.map(it, ID) }
    }

    private suspend fun remoteOrNull(bundled: CuratedFile): CuratedFile? {
        val url = BuildConfig.CURATED_URL
        if (url.isBlank()) return null
        val data = try {
            fetcher.getBytes(url) to fetcher.getBytes("$url.sig", maxBytes = 1_024)
        } catch (_: IOException) {
            return null // hors ligne : la version embarquée suffit
        }
        val remote = verifiedFile(data.first, data.second)
        return remote.takeIf { parseInstant(it.generatedAt) >= parseInstant(bundled.generatedAt) }
    }

    internal fun verifiedFile(content: ByteArray, signatureBase64: ByteArray): CuratedFile {
        verify(content, signatureBase64)
        return try {
            json.decodeFromString<CuratedFile>(content.toString(Charsets.UTF_8))
                .also { if (it.version != 1) throw InvalidSourceDataException("Version inconnue : ${it.version}") }
        } catch (e: SerializationException) {
            throw InvalidSourceDataException("Sélection illisible", e)
        } catch (e: IllegalArgumentException) {
            throw InvalidSourceDataException("Sélection illisible", e)
        }
    }

    private fun parseInstant(raw: String): Instant =
        try { Instant.parse(raw) } catch (e: DateTimeParseException) { throw InvalidSourceDataException("Date invalide", e) }

    companion object {
        const val ID = "selection"
        private const val ASSET = "curated/events.json"

        /** Clé publique Ed25519 (32 octets, base64). Générée par tools/sign-curated.sh. */
        internal const val PUBLIC_KEY = "jUhHNqR7UqkV/Bsp7oGB624sbZwOwsMYxx1gHBlyPA0="

        internal fun verify(content: ByteArray, signatureBase64: ByteArray) {
            val signature = try {
                Base64.getDecoder().decode(signatureBase64.toString(Charsets.US_ASCII).trim())
            } catch (_: IllegalArgumentException) {
                throw SignatureException("Signature mal formée")
            }
            try {
                Ed25519Verify(Base64.getDecoder().decode(PUBLIC_KEY)).verify(signature, content)
            } catch (_: GeneralSecurityException) {
                throw SignatureException("Signature invalide")
            }
        }
    }
}

internal object CuratedMapper {
    private val zone = ZoneId.of("Europe/Paris")
    private val safeId = Regex("[a-z0-9-]{1,80}")

    fun map(e: CuratedEvent, sourceId: String): Event? {
        if (!safeId.matches(e.id)) return null
        val start = parse(e.start, endOfDay = false) ?: return null
        val end = parse(e.end ?: e.start, endOfDay = true) ?: return null
        return Event(
            id = "$sourceId:${e.id}",
            sourceId = sourceId,
            title = e.title,
            description = e.description,
            startsAtEpochMs = start,
            endsAtEpochMs = end,
            format = runCatching { EventFormat.valueOf(e.format) }.getOrDefault(EventFormat.ONSITE),
            kind = runCatching { EventKind.valueOf(e.kind) }.getOrDefault(EventKind.OTHER),
            url = e.url,
            organizer = e.organizer,
            location = e.location,
            latitude = e.latitude,
            longitude = e.longitude,
            beginnerFriendly = e.beginnerFriendly,
            free = e.free ?: e.price?.let { it.contains("gratuit", ignoreCase = true) },
            price = e.price,
        )
    }

    private fun parse(raw: String, endOfDay: Boolean): Long? = try {
        if (raw.length == 10) {
            val d = LocalDate.parse(raw)
            (if (endOfDay) d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            else d.atStartOfDay(zone).toInstant().toEpochMilli())
        } else {
            OffsetDateTime.parse(raw).toInstant().toEpochMilli()
        }
    } catch (_: DateTimeParseException) {
        null
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CuratedModule {
    @Binds @IntoSet
    abstract fun bindSource(source: CuratedSource): EventSource
}
