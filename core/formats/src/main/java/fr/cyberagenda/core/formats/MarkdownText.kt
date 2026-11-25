package fr.cyberagenda.core.formats

/**
 * Markdown (descriptions CTFtime et OpenAgenda) → texte simple affichable tel quel :
 * liens réduits à leur texte, gras/italique retirés (même sur plusieurs lignes), titres sans
 * dièses, puces « • », lignes de séparation supprimées. Linéaire, sans regex imbriquée risquée.
 */
object MarkdownText {
    private const val MAX_INPUT = 20_000
    // [texte](url) ou [texte](url "infobulle")
    private val link = Regex("!?\\[([^\\]\\n]{0,200})]\\([^)\\s]{0,500}(?:\\s+\"[^\"\\n]{0,200}\")?\\)")
    private val rule = Regex("(?m)^[ \\t]*([*_-][ \\t]*){3,}$\\n?")
    private val bold = Regex("(\\*\\*|__)([^*_]{1,2000}?)\\1")
    // *italique* / _italique_ : pas d'espace juste à l'intérieur (« 2 * 3 » reste intact).
    private val italic = Regex("(?<![\\w*_])([*_])(?=\\S)([^*_\\n]{1,300}?)(?<=\\S)\\1(?![\\w*_])")
    private val heading = Regex("(?m)^[ \\t]{0,3}#{1,6}[ \\t]+")
    private val bullet = Regex("(?m)^[ \\t]*[*+-][ \\t]+")
    private val trailingSpaces = Regex("(?m)[ \\t\\u00A0]+$")
    private val blankLines = Regex("\\n{3,}")

    fun plain(md: String): String = md.take(MAX_INPUT)
        .replace(link, "$1")
        .replace(rule, "")
        .replace(bold, "$2")
        .replace(italic, "$2")
        .replace(heading, "")
        .replace(bullet, "• ")
        .replace("**", "")
        .replace(trailingSpaces, "")
        .replace(blankLines, "\n\n")
        .trim()
}
