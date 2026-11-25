package fr.cyberagenda.feature.settings

import fr.cyberagenda.core.model.EventFormat
import fr.cyberagenda.core.model.EventKind
import fr.cyberagenda.core.model.TextSanitizer
import fr.cyberagenda.core.network.SafeUrl
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Tarif indiqué par la personne qui propose l'événement. */
enum class PriceChoice { UNKNOWN, FREE, PAID }

/** Proposition d'événement saisie par l'utilisateur. Rien n'est envoyé sans son action. */
data class Proposal(
    val title: String = "",
    val kind: EventKind = EventKind.MEETUP,
    val format: EventFormat = EventFormat.ONSITE,
    val start: LocalDate? = null,
    val end: LocalDate? = null,
    /** Heures facultatives (heure de Paris) ; sans heure, l'événement s'affiche « horaire inconnu ». */
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val location: String = "",
    val url: String = "",
    val organizer: String = "",
    val description: String = "",
    val beginnerFriendly: Boolean = false,
    val price: PriceChoice = PriceChoice.UNKNOWN,
    /** Montant en clair si payant : « 20 € », « 10–40 € ». */
    val priceText: String = "",
) {
    private val lastDay: LocalDate? get() = end ?: start

    /** Messages d'erreur par champ (vide = proposition valide). */
    fun errors(today: LocalDate = LocalDate.now()): Map<String, String> = buildMap {
        if (title.trim().length !in 3..150) put("title", "Entre 3 et 150 caractères")
        if (start == null) put("start", "Date obligatoire")
        else if (start < today) put("start", "L'événement doit être à venir")
        if (start != null && end != null && end < start) put("end", "La fin doit suivre le début")
        if (start != null && end != null && end > start.plusDays(60)) put("end", "60 jours maximum")
        if (endTime != null && startTime == null) put("startTime", "Indiquez aussi l'heure de début")
        if (startTime != null && endTime != null && lastDay == start && endTime <= startTime) put("endTime", "L'heure de fin doit suivre le début")
        if (format != EventFormat.ONLINE && location.trim().length < 2) put("location", "Lieu obligatoire pour un événement sur place")
        if (!SafeUrl.isHttps(url.trim())) put("url", "Lien https:// valide obligatoire")
        if (price == PriceChoice.PAID && priceText.trim().isEmpty()) put("priceText", "Indiquez le montant (ex. « 20 € »)")
        if (priceText.length > 60) put("priceText", "60 caractères maximum")
        if (description.length > 1_000) put("description", "1000 caractères maximum")
    }

    /** Date (journée entière) ou date et heure de Paris avec décalage : format de la sélection signée. */
    private fun stamp(date: LocalDate, time: LocalTime?): String =
        if (time == null) date.toString() else ZonedDateTime.of(date, time, PARIS).format(ISO_OFFSET)

    private val startStamp: String get() = stamp(start!!, startTime)

    private val endStamp: String get() = when {
        endTime != null -> stamp(lastDay!!, endTime)
        // Heure de début connue, fin inconnue, sur une journée : l'app affiche « à 18:30 ».
        startTime != null && lastDay == start -> startStamp
        else -> lastDay.toString()
    }

    private val priceLabel: String? get() = when (price) {
        PriceChoice.UNKNOWN -> null
        PriceChoice.FREE -> "Gratuit"
        PriceChoice.PAID -> TextSanitizer.clean(priceText, 60).ifBlank { null }
    }

    /** Entrée prête à coller dans curated/events.json (puis à signer). */
    fun toCuratedJson(): String {
        val clean = { s: String, max: Int -> TextSanitizer.clean(s, max) }
        val entry = CuratedEntry(
            id = slug(title) + "-" + (start?.year ?: ""),
            title = clean(title, 150),
            kind = kind.name,
            format = format.name,
            start = startStamp,
            end = endStamp,
            location = clean(location, 200).ifBlank { null },
            url = SafeUrl.parseHttps(url.trim())?.toString().orEmpty(),
            organizer = clean(organizer, 150).ifBlank { null },
            description = clean(description, 1_000),
            beginnerFriendly = beginnerFriendly,
            free = when (price) { PriceChoice.FREE -> true; PriceChoice.PAID -> false; PriceChoice.UNKNOWN -> null },
            price = priceLabel,
        )
        return json.encodeToString(CuratedEntry.serializer(), entry)
    }

    /** « 12/03/2027 18:30 → 20:00 », « du 12/03/2027 au 13/03/2027 », etc. */
    private fun whenLabel(): String {
        val d = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val s = start!!.format(d) + (startTime?.let { " $it" } ?: "")
        return when {
            lastDay == start && endTime != null -> "$s → $endTime"
            lastDay == start -> s + if (startTime == null) " (horaire inconnu)" else ""
            else -> "du $s au ${lastDay!!.format(d)}" + (endTime?.let { " $it" } ?: "")
        }
    }

    fun toMessage(): String = buildString {
        appendLine("Proposition d'événement pour CyberAgenda")
        appendLine()
        appendLine("Titre : ${TextSanitizer.clean(title, 150)}")
        appendLine("Type : ${kind.name} · ${format.name}")
        appendLine("Quand : ${whenLabel()}")
        if (location.isNotBlank()) appendLine("Lieu : ${TextSanitizer.clean(location, 200)}")
        appendLine("Lien : ${url.trim()}")
        if (organizer.isNotBlank()) appendLine("Organisation : ${TextSanitizer.clean(organizer, 150)}")
        appendLine("Tarif : ${priceLabel ?: "non précisé"}")
        if (beginnerFriendly) appendLine("Ouvert aux débutants : oui")
        if (description.isNotBlank()) { appendLine(); appendLine(TextSanitizer.clean(description, 1_000)) }
        appendLine()
        appendLine("--- Entrée pour la sélection (à vérifier avant de signer) ---")
        appendLine(toCuratedJson())
    }

    /**
     * Page « nouvelle issue » du dépôt, pré-remplie : le modèle de formulaire
     * .github/ISSUE_TEMPLATE/proposer-evenement.yml reçoit chaque champ par son identifiant.
     * La description est raccourcie si besoin pour rester sous la longueur d'URL acceptée.
     */
    fun toGitHubIssueUrl(repo: String): String {
        fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8)
        fun build(desc: String): String {
            val p = copy(description = desc)
            val params = listOf(
                "template" to ISSUE_TEMPLATE,
                "title" to "[Proposition] ${TextSanitizer.clean(title, 100)}",
                "titre" to TextSanitizer.clean(title, 150),
                "type" to "${kind.name} · ${format.name}",
                "quand" to whenLabel(),
                "lieu" to TextSanitizer.clean(location, 200),
                "lien" to url.trim(),
                "organisation" to TextSanitizer.clean(organizer, 150),
                "tarif" to (priceLabel ?: "Non précisé"),
                "debutants" to if (beginnerFriendly) "Oui" else "Non précisé",
                "description" to TextSanitizer.clean(desc, 1_000),
                "entree" to p.toCuratedJson(),
            ).filter { it.second.isNotBlank() }
            return "https://github.com/$repo/issues/new?" + params.joinToString("&") { (k, v) -> k + "=" + enc(v) }
        }
        var desc = description
        var url = build(desc)
        while (url.length > MAX_URL && desc.isNotEmpty()) {
            desc = desc.take((desc.length * 0.8).toInt()).trimEnd() + "…"
            if (desc == "…") desc = ""
            url = build(desc)
        }
        return url
    }

    @Serializable
    private data class CuratedEntry(
        val id: String, val title: String, val kind: String, val format: String,
        val start: String, val end: String, val location: String? = null, val url: String,
        val organizer: String? = null, val description: String, val beginnerFriendly: Boolean,
        val free: Boolean? = null, val price: String? = null,
    )

    companion object {
        const val ISSUE_TEMPLATE = "proposer-evenement.yml"
        /** Longueur d'URL au-delà de laquelle certains navigateurs ou GitHub refusent la page. */
        const val MAX_URL = 7_500
        private val PARIS: ZoneId = ZoneId.of("Europe/Paris")
        private val ISO_OFFSET: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
        private val json = Json { prettyPrint = true; explicitNulls = false }
        private fun slug(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9]+"), "-").trim('-').take(60)
    }
}
