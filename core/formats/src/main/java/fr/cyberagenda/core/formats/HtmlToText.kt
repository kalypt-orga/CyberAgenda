package fr.cyberagenda.core.formats

/**
 * Convertit une description HTML en texte brut. Le résultat est affiché comme du texte,
 * jamais interprété : aucun lien, script ou image n'est conservé.
 *
 * Toutes les étapes sont en temps linéaire (pas de retour arrière exploitable par une
 * description piégée) : les blocs script/style sont retirés par un parcours manuel et les
 * motifs de balise ne peuvent pas franchir un « < ».
 */
object HtmlToText {
    private const val MAX_INPUT = 20_000
    private val rawBlockStart = Regex("(?i)<(script|style)\\b")
    private val lineBreak = Regex("(?i)<br\\s*/?>")
    private val blockEnd = Regex("(?i)</(p|div|h[1-6]|li|tr|ul|ol|blockquote)\\s*>")
    private val listItem = Regex("(?i)<li\\b[^<>]{0,2000}>")
    private val anyTag = Regex("<[^<>]{0,2000}>")
    private val entity = Regex("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z]{2,8});")
    private val spaces = Regex("[ \\t\\u00A0]+")
    private val blankLines = Regex("\\n{3,}")

    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "eacute" to "é", "egrave" to "è", "ecirc" to "ê", "agrave" to "à", "acirc" to "â",
        "ccedil" to "ç", "ocirc" to "ô", "ucirc" to "û", "ugrave" to "ù", "icirc" to "î",
        "iuml" to "ï", "euml" to "ë", "oelig" to "œ", "laquo" to "«", "raquo" to "»",
        "rsquo" to "’", "lsquo" to "‘", "ldquo" to "“", "rdquo" to "”", "hellip" to "…",
        "ndash" to "–", "mdash" to "—", "euro" to "€", "Eacute" to "É", "Agrave" to "À",
    )

    fun convert(html: String): String {
        var s = html.take(MAX_INPUT)
        s = stripRawBlocks(s)
        s = lineBreak.replace(s, "\n")
        s = blockEnd.replace(s, "\n")
        s = listItem.replace(s, "• ")
        s = anyTag.replace(s, "")
        s = entity.replace(s) { m -> decode(m.groupValues[1]) ?: m.value }
        s = s.lines().joinToString("\n") { spaces.replace(it, " ").trim() }
        return blankLines.replace(s, "\n\n").trim()
    }

    /** Retire les blocs script/style ; un bloc jamais fermé emporte tout le reste du texte. */
    private fun stripRawBlocks(s: String): String {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val open = rawBlockStart.find(s, i) ?: break
            out.append(s, i, open.range.first)
            val close = s.indexOf("</" + open.groupValues[1], open.range.last + 1, ignoreCase = true)
            val end = if (close < 0) -1 else s.indexOf('>', close)
            i = if (end < 0) s.length else end + 1
        }
        if (i < s.length) out.append(s, i, s.length)
        return out.toString()
    }

    private fun decode(e: String): String? {
        if (!e.startsWith("#")) return named[e]
        val code = if (e.startsWith("#x") || e.startsWith("#X")) e.drop(2).toIntOrNull(16) else e.drop(1).toIntOrNull()
        // On refuse les points de code invalides et les caractères de contrôle.
        if (code == null || code < 0x20 || code > 0x10FFFF || code in 0xD800..0xDFFF) return ""
        return String(Character.toChars(code))
    }
}
