package fr.cyberagenda.core.model

/**
 * Quand le lieu fourni par la source est imprécis (ville seule, sans numéro de rue) mais que
 * la description contient une adresse postale complète, on utilise cette adresse : elle sert
 * au résumé, à l'itinéraire et au placement sur la carte.
 *
 * Les données structurées de la source priment sur le texte libre : rien n'est changé si la
 * source fournit déjà des coordonnées, et l'adresse trouvée doit être dans la ville déjà
 * connue (une adresse de parking ou d'un autre lieu cité ne détourne pas l'itinéraire).
 */
object AddressHints {
    private const val STREET = "(?:rue|avenue|av\\.|boulevard|bd|place|all[ée]e|chemin|route|quai|impasse|cours|esplanade|parvis|square|promenade)"
    /** « 1 Rue du 19 Mars 1962, 92230 Gennevilliers » */
    private val fullAddress = Regex(
        "\\b\\d{1,4}(?:\\s?(?:bis|ter))?,?\\s+$STREET\\b[^\\n,;]{2,60},?\\s+\\d{5}\\s+[A-ZÀ-Ÿ][\\p{L}'\\- ]{1,40}",
        RegexOption.IGNORE_CASE,
    )
    private val streetInLocation = Regex("\\b\\d{1,4}(?:\\s?(?:bis|ter))?,?\\s+$STREET\\b", RegexOption.IGNORE_CASE)

    fun apply(event: Event): Event {
        if (event.format == EventFormat.ONLINE) return event
        if (event.latitude != null) return event // coordonnées fournies par la source : elles font foi
        val current = event.location
        if (current != null && streetInLocation.containsMatchIn(current)) return event // déjà précis
        val known = current?.let(::words).orEmpty()
        val found = fullAddress.findAll(event.description)
            .map { it.value.trim().trimEnd(',', '.', ' ') }
            .firstOrNull { address -> known.isEmpty() || words(address).any { it in known } }
            ?: return event
        // L'adresse trouvée remplace le lieu ; les coordonnées seront recalculées par le géocodage.
        return event.copy(location = found.take(200), latitude = null, longitude = null)
    }

    /** Mots de 3 lettres ou plus, en minuscules et sans accents (« Darnétal » → « darnetal »). */
    private fun words(text: String): Set<String> =
        java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .split(Regex("[^a-z]+"))
            .filter { it.length >= 3 && it !in noise }
            .toSet()

    private val noise = setOf("france", "rue", "avenue", "place", "bis", "ter")
}
