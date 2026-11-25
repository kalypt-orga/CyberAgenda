package fr.cyberagenda.core.model

/**
 * Déduit le tarif quand la source ne le fournit pas, à partir de la description.
 * Le « gratuit » n'est retenu que s'il porte sur l'événement lui-même (participation,
 * inscription, entrée, conférence…), pas sur autre chose (« ces logiciels sont gratuits »).
 * Un prix à gagner (« 1re place : 500 € ») n'empêche rien ; seuls des indices de coût
 * d'entrée (tarif, billet, « 20 € par personne »…) font préférer « non précisé ».
 * Tout montant cité hors contexte de récompense (« inscription gratuite puis 250 € »)
 * fait aussi renoncer au « Gratuit » : dans le doute, on n'affiche rien.
 * Motifs bornés et ancrés : aucun retour arrière quadratique sur une longue suite de chiffres.
 */
object PriceHints {
    private val eventFree = listOf(
        Regex("\\b(participation|inscription|entr[ée]e|acc[èe]s|atelier|conf[ée]rence|conf|[ée]v[ée]nement|soir[ée]e|rencontre|ctf|journ[ée]e|formation)\\b[^.\\n]{0,30}\\bgratuit(e|s|es)?\\b", RegexOption.IGNORE_CASE),
        Regex("\\bgratuit(e)?s? et ouvert", RegexOption.IGNORE_CASE),
        Regex("\\b(entr[ée]e|acc[èe]s|participation) libre\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(is|it's|entirely|completely|totally)( an?)? free\\b|\\bfor free\\b|\\bfree (entry|admission|of charge|registration|to (join|play|participate|attend))\\b", RegexOption.IGNORE_CASE),
    )
    private val entryCost = Regex(
        "\\b(tarifs?|billets?|billetterie|payant(e|s)?|prix d'entr[ée]e|ticket(s)? (price|cost)|droits? d'inscription)\\b" +
            "|€\\s*(HT|TTC)|\\b\\d{1,6}\\s*€\\s*(l'entr|par personne|la journ|le pass|/\\s*pers)",
        RegexOption.IGNORE_CASE,
    )
    /** Un montant : « 40 € », « 40 euros », « 99 EUR », « 300 USD », « $50 ». */
    private val amount = Regex(
        "\\b\\d{1,6}(?:[.,]\\d{1,2})?\\s?(?:€|euros?\\b|eur\\b|usd\\b|\\$)|[€$]\\s?\\d{1,6}\\b",
        RegexOption.IGNORE_CASE,
    )
    /** Ce qui précède un montant à gagner (« Récompenses - 1re place : 500 € »). */
    private val prizeContext = Regex(
        "gagner|r[ée]compense|dotation|\\blots?\\b|cash ?prize|prizes?\\b|bounty|podium|\\b\\d+\\s?(?:re|er|e|ème|st|nd|rd|th)\\s+place",
        RegexOption.IGNORE_CASE,
    )

    fun apply(event: Event): Event {
        if (event.price != null || event.free != null) {
            return if (event.price == null && event.free == true) event.copy(price = "Gratuit") else event
        }
        val text = event.title + "\n" + event.description
        return if (eventFree.any { it.containsMatchIn(text) } && !entryCost.containsMatchIn(text) && !citesEntryAmount(text)) {
            event.copy(free = true, price = "Gratuit")
        } else event
    }

    /** Vrai si un montant est cité ailleurs que dans une liste de récompenses (même ligne, 60 caractères avant). */
    private fun citesEntryAmount(text: String): Boolean = amount.findAll(text).any { m ->
        val lineStart = text.lastIndexOf('\n', m.range.first) + 1
        val before = text.substring(maxOf(lineStart, m.range.first - 60), m.range.first)
        !prizeContext.containsMatchIn(before)
    }
}
