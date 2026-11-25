package fr.cyberagenda.source.openagenda

import dagger.Binds
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
import fr.cyberagenda.core.formats.MarkdownText
import fr.cyberagenda.core.network.HttpFetcher
import fr.cyberagenda.core.network.HttpStatusException
import fr.cyberagenda.core.network.SafeUrl
import fr.cyberagenda.core.network.mapEachSafely
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import javax.inject.Inject

/**
 * Événements publics d'OpenAgenda (agendas d'associations, médiathèques, universités, French
 * Tech…), lus avec une clé PUBLIQUE en lecture seule.
 *
 * - Recherche sur tous les agendas (« transverse ») si OpenAgenda l'a ouverte pour la clé ;
 * - sinon (refus 403), lecture d'une courte liste d'agendas choisis pour leur contenu cyber.
 * Dans les deux cas, seuls les événements qui parlent réellement de cybersécurité sont gardés.
 * Le passage de l'un à l'autre est automatique : aucune mise à jour de l'app n'est nécessaire.
 */
class OpenAgendaSource @Inject constructor(
    private val fetcher: HttpFetcher,
    private val json: Json,
) : EventSource {

    override val meta = SourceMeta(
        id = ID,
        name = "OpenAgenda",
        description = "Ateliers, webinaires et conférences publiés par des associations, médiathèques, " +
            "universités et réseaux du numérique, filtrés sur la cybersécurité.",
        homepage = "https://openagenda.com",
        kinds = setOf(EventKind.WORKSHOP, EventKind.CONFERENCE, EventKind.MEETUP),
        order = 4,
        available = BuildConfig.OPENAGENDA_KEY.isNotBlank(),
    )

    private val auth = mapOf("key" to BuildConfig.OPENAGENDA_KEY)

    // Quota OpenAgenda : 100 000 événements lus par jour pour TOUTE l'app (une seule clé).
    // ~160 événements par synchronisation, une fois par jour : ~600 appareils actifs par jour.
    override val refreshIntervalMs: Long get() = 24 * HOUR
    override val forcedRefreshFloorMs: Long get() = 2 * HOUR

    override suspend fun fetch(): List<Event> {
        val raw = try {
            TRANSVERSE_QUERIES.flatMap { q -> page(eventsUrl("events", q)) }
        } catch (e: HttpStatusException) {
            if (e.code != 403 && e.code != 401) throw e
            // Recherche transverse pas (encore) ouverte pour la clé : agendas choisis.
            AGENDAS.flatMap { uid -> page(eventsUrl("agendas/$uid/events", null)) }
        }
        val now = System.currentTimeMillis()
        return raw.distinctBy { it.uid }
            .filter { OpenAgendaMapper.isCyber(it) }
            .distinctBy { OpenAgendaMapper.sameEventKey(it) }
            .let(OpenAgendaMapper::dropNestedDuplicates)
            .mapEachSafely { OpenAgendaMapper.map(it, ID, now) }
            .flatten()
    }

    private fun eventsUrl(path: String, search: String?): String =
        HttpUrl.Builder().scheme("https").host(HOST)
            .addPathSegments("v2/$path")
            .apply { if (search != null) addQueryParameter("search", search) }
            .addQueryParameter("relative[]", "current")
            .addQueryParameter("relative[]", "upcoming")
            .addQueryParameter("monolingual", "fr")
            .addQueryParameter("detailed", "1")
            .addQueryParameter("size", PAGE_SIZE.toString())
            .build().toString()

    /** Une page de résultats ; chaque événement est décodé séparément (un élément invalide est ignoré). */
    private suspend fun page(url: String): List<OaEvent> {
        val body = fetcher.getText(url, headers = auth)
        return try {
            val events = json.parseToJsonElement(body).jsonObject["events"]?.jsonArray ?: JsonArray(emptyList())
            events.mapNotNull { e -> runCatching { json.decodeFromJsonElement<OaEvent>(e) }.getOrNull() }
        } catch (e: SerializationException) {
            throw InvalidSourceDataException("Réponse OpenAgenda illisible", e)
        } catch (e: IllegalArgumentException) {
            throw InvalidSourceDataException("Réponse OpenAgenda illisible", e)
        }
    }

    companion object {
        const val ID = "openagenda"
        private const val HOST = "api.openagenda.com"
        private const val PAGE_SIZE = 100
        private const val HOUR = 3_600_000L

        /**
         * Recherches choisies sur mesure (novembre 2026) : « cyber » couvre aussi « cybersécurité »
         * (~70 événements) ; les autres ajoutent des ateliers grand public (arnaques, piratage,
         * données personnelles) pour peu d'événements lus. « vie privée » ou « sécurité numérique »
         * renvoient des centaines d'événements hors sujet pour quelques-uns utiles : exclues.
         */
        private val TRANSVERSE_QUERIES = listOf("cyber", "piratage", "arnaques", "RGPD", "données personnelles")

        /**
         * Agendas lus tant que la recherche transverse n'est pas ouverte (UID OpenAgenda) :
         * France Num (TPE-PME), Campus Cyber Hauts-de-France, EuraTechnologies, CyberSchool
         * (Rennes), IRISA, Journée Expert Cybersécurité, CYBER RÉUNION.
         */
        internal val AGENDAS = listOf(97575085L, 90279115L, 12336727L, 6486125L, 26529132L, 73725802L, 18987801L)
    }
}

@Serializable
internal data class OaEvent(
    val uid: Long,
    val title: String? = null,
    val description: String? = null,
    val longDescription: String? = null,
    val keywords: JsonElement? = null,
    val timings: List<OaTiming> = emptyList(),
    val location: OaLocation? = null,
    val attendanceMode: Int? = null,
    val status: Int? = null,
    val registration: List<OaRegistration> = emptyList(),
    val canonicalUrl: String? = null,
    val conditions: String? = null,
    val originAgenda: OaAgenda? = null,
    val rights: OaRights? = null,
    val updatedAt: String? = null,
)

@Serializable internal data class OaTiming(val begin: String, val end: String)

@Serializable
internal data class OaLocation(
    val name: String? = null,
    val address: String? = null,
    val city: String? = null,
    val postalCode: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val countryCode: String? = null,
)

@Serializable internal data class OaRegistration(val type: String? = null, val value: String? = null)
@Serializable internal data class OaAgenda(val title: String? = null)
@Serializable internal data class OaRights(val license: OaLicense? = null, val licensor: OaLicensor? = null)
@Serializable internal data class OaLicense(val name: String? = null)
@Serializable internal data class OaLicensor(val name: String? = null, @SerialName("agendaTitle") val agendaTitle: String? = null)

internal object OpenAgendaMapper {
    private const val CANCELLED = 6
    private const val MAX_SESSIONS = 12
    private const val PUBLISHER_CUT = 500
    const val CUT_NOTICE = "Description abrégée par l'organisateur : la suite est sur son site (« Voir le site »)."
    private val UPDATED = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(java.time.ZoneId.of("Europe/Paris"))

    /** Ce qui parle réellement de cybersécurité (pas « hack » seul : il attrape les hackathons). */
    private val cyber = Regex(
        // « cyber » sauf dans des noms de lieux ou de genres sans rapport (Cyber-base, cyberpunk…).
        "cyber(?![\\s-]?(base|punk|caf[ée]|espace|[ée]crivain))\\p{L}*|" +
            "s[ée]curit[ée] (num[ée]rique|informatique|en ligne|sur internet|des donn[ée]es|des syst[èe]mes)|" +
            "attaques? informatiques?|en toute s[ée]curit[ée]|arnaques? (au|par) (sms|t[ée]l[ée]phone|e-?mail|courriel)|" +
            "s[ée]curiser (ses|vos|son|votre|sa) (donn|compte|t[ée]l[ée]phone|ordinateur|smartphone)|" +
            "prot[ée]ger (ses|vos|nos|tes|les) donn[ée]es|donn[ée]es,? un tr[ée]sor|" +
            "vie priv[ée]e|mots? de passe|phishing|hame[çc]onnage|arnaques? (en ligne|sur internet|num[ée]riques?)|" +
            "ran[çc]ongiciel|ransomware|hacking|hackers?\\b|capture the flag|\\bctf\\b|\\brgpd\\b|\\bnis ?2\\b|\\bosint\\b|" +
            "chiffrement|cryptograph|fuites? de donn[ée]es|piratage|identit[ée] num[ée]rique|protection des donn[ée]es",
        RegexOption.IGNORE_CASE,
    )
    private val workshop = Regex("atelier|initiation|formation|install party|permanence", RegexOption.IGNORE_CASE)
    private val conference = Regex("conf[ée]rence|webinaire|webinar|table ronde|colloque|forum|salon|matinale|keynote", RegexOption.IGNORE_CASE)
    private val ctf = Regex("\\bctf\\b|capture the flag|challenge de s[ée]curit", RegexOption.IGNORE_CASE)
    private val leadingConference = Regex("^\\W*(salon|forum|colloque|conf[ée]rence|webinaire|webinar|table ronde)\\b", RegexOption.IGNORE_CASE)
    private val meetup = Regex("caf[ée]|ap[ée]ro|rencontre|meetup|soir[ée]e", RegexOption.IGNORE_CASE)
    private val beginner = Regex("d[ée]butant|initiation|grand public|tout public|tous publics|sensibilisation|sans pr[ée]requis|bases", RegexOption.IGNORE_CASE)
    private val markdownLink = Regex("!?\\[([^\\]]{0,200})]\\([^)]{0,500}\\)")

    /**
     * Vraiment cyber : un terme cyber dans le titre ; sinon au moins deux termes
     * cyber DIFFÉRENTS dans le résumé et la description (une mention en passant ne suffit pas :
     * beaucoup d'événements citent « la cybersécurité » parmi dix autres thèmes).
     */
    fun isCyber(e: OaEvent): Boolean {
        if (cyber.containsMatchIn(e.title.orEmpty())) return true
        // Mots-clés comptés avec la description : certains organisateurs étiquettent
        // « cybersécurité » des événements qui n'en parlent pas.
        val body = listOfNotNull(e.description, e.longDescription, keywords(e)).joinToString(" ")
        return cyber.findAll(body).map { it.value.lowercase() }.toSet().size >= 2
    }

    /**
     * Même événement publié par deux organismes sous des titres différents (« Futur Connect » et
     * « Futur Connect - Forum Cyber, IA & Tech… ») : même ville, mêmes horaires, titre de l'un
     * contenu dans l'autre. On garde le titre le plus complet.
     */
    fun dropNestedDuplicates(events: List<OaEvent>): List<OaEvent> {
        fun slot(e: OaEvent) = e.location?.city.orEmpty().lowercase() + "|" + e.timings.firstOrNull()?.let { it.begin + it.end }
        return events.groupBy(::slot).values.flatMap { group ->
            val sorted = group.sortedByDescending { it.title.orEmpty().length }
            sorted.filterIndexed { i, e ->
                val t = comparable(e.title.orEmpty())
                val head = titleHead(e)
                t.isEmpty() || sorted.take(i).none { other ->
                    comparable(other.title.orEmpty()).contains(t) ||
                        // « Forum Cyber et Tech » dont la description dit « Forum FUTUR CONNECT » :
                        // le début du titre de l'un apparaît dans le texte de l'autre.
                        titleHead(other).let { h -> h.length >= 8 && comparable(text(e)).contains(h) } ||
                        (head.length >= 8 && comparable(text(other)).contains(head))
                }
            }
        }
    }

    /** Début distinctif d'un titre (avant « - », « : », « | »), comparable. */
    private fun titleHead(e: OaEvent) = comparable(e.title.orEmpty().split(" - ", " – ", " : ", " | ").first())

    /** Même événement repris par plusieurs agendas (agrégation) : même titre, même première date. */
    fun sameEventKey(e: OaEvent): String =
        e.title.orEmpty().lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim() + "|" + e.timings.firstOrNull()?.begin

    private fun text(e: OaEvent) = listOfNotNull(e.title, e.description, e.longDescription, keywords(e)).joinToString(" ")

    private fun keywords(e: OaEvent): String? = when (val k = e.keywords) {
        is JsonArray -> k.mapNotNull { (it as? JsonPrimitive)?.content }.joinToString(" ")
        is JsonObject -> k.values.flatMap { v -> (v as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty() }.joinToString(" ")
        else -> null
    }

    /** Un événement OpenAgenda → une occurrence par séance à venir (regroupées par l'app en « +N dates »). */
    fun map(e: OaEvent, sourceId: String, now: Long): List<Event>? {
        if (e.status == CANCELLED) return null
        val title = e.title?.trim().orEmpty().ifEmpty { return null }
        val all = text(e)
        val format = when (e.attendanceMode) {
            2 -> EventFormat.ONLINE
            3 -> EventFormat.HYBRID
            else -> EventFormat.ONSITE
        }
        val loc = e.location
        if (format != EventFormat.ONLINE && loc?.countryCode != null && loc.countryCode.uppercase() != "FR") return null
        val url = e.registration.firstOrNull { it.type == "link" && SafeUrl.isHttps(it.value.orEmpty()) }?.value
            ?: e.canonicalUrl?.takeIf { SafeUrl.isHttps(it) }
            ?: return null
        val kind = when {
            ctf.containsMatchIn(all) -> EventKind.CTF
            // « Salon Formations… », « Forum… » : le premier mot dit la nature de l'événement.
            leadingConference.containsMatchIn(title) -> EventKind.CONFERENCE
            workshop.containsMatchIn(title) -> EventKind.WORKSHOP
            conference.containsMatchIn(title) -> EventKind.CONFERENCE
            meetup.containsMatchIn(title) -> EventKind.MEETUP
            workshop.containsMatchIn(all) -> EventKind.WORKSHOP
            conference.containsMatchIn(all) -> EventKind.CONFERENCE
            else -> EventKind.OTHER
        }
        val licensor = e.rights?.licensor?.name ?: e.originAgenda?.title
        // Licence Ouverte 2.0 : citer l'organisme qui publie ET la date de dernière mise à jour.
        val updated = e.updatedAt?.let(::parse)?.let { "mis à jour le " + UPDATED.format(java.time.Instant.ofEpochMilli(it)) }
        val credit = listOfNotNull(licensor?.let { "Source : $it via OpenAgenda" }, e.rights?.license?.name, updated).joinToString(" — ")
        // Certains organismes (dont France Travail) publient sur OpenAgenda un texte coupé net à
        // 500 caractères, parfois en plein mot : on le signale au lieu de laisser une phrase en suspens.
        val cut = e.longDescription?.let { it.length == PUBLISHER_CUT && it.trimEnd().last().isLetterOrDigit() } == true
        val long = e.longDescription?.let(::plainText)?.let { if (cut) it.trimEnd() + "…\n\n" + CUT_NOTICE else it }
        // Le résumé est souvent la première phrase de la description longue : pas de doublon.
        val summary = e.description?.takeUnless { d -> long != null && comparable(long).contains(comparable(d)) }
        val description = listOfNotNull(summary, long, credit.ifEmpty { null })
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n\n")
        val place = loc?.let {
            val dept = it.postalCode?.let { pc -> if (pc.startsWith("97")) pc.take(3) else pc.take(2) }
            listOfNotNull(it.name?.takeIf { n -> n.isNotBlank() && n != it.city }, it.city?.let { c -> if (dept != null) "$c ($dept)" else c })
                .joinToString(", ").ifEmpty { null }
        }
        return e.timings.mapNotNull { t -> parse(t.begin)?.let { b -> parse(t.end)?.let { en -> b to en } } }
            .filter { (_, end) -> end >= now }
            .sortedBy { it.first }
            .take(MAX_SESSIONS)
            .map { (start, end) ->
                Event(
                    id = "$sourceId:${e.uid}-$start",
                    sourceId = sourceId,
                    title = title,
                    description = description,
                    startsAtEpochMs = start,
                    endsAtEpochMs = end,
                    format = format,
                    kind = kind,
                    url = url,
                    organizer = e.originAgenda?.title,
                    location = if (format == EventFormat.ONLINE) null else place,
                    latitude = if (format == EventFormat.ONLINE) null else loc?.latitude,
                    longitude = if (format == EventFormat.ONLINE) null else loc?.longitude,
                    beginnerFriendly = beginner.containsMatchIn(all),
                    price = e.conditions?.takeIf { it.isNotBlank() && it.length <= 60 },
                )
            }
    }

    internal fun plainText(md: String): String = MarkdownText.plain(md)

    private fun comparable(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun parse(raw: String): Long? =
        try { OffsetDateTime.parse(raw).toInstant().toEpochMilli() } catch (_: DateTimeParseException) { null }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class OpenAgendaModule {
    @Binds @IntoSet
    abstract fun bindSource(source: OpenAgendaSource): EventSource
}
