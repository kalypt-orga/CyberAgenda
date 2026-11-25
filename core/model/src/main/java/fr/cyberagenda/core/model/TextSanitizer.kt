package fr.cyberagenda.core.model

import java.text.Normalizer

/**
 * Nettoie le texte provenant de sources externes. L'app n'affiche que du texte brut
 * (jamais de HTML ni de WebView) ; ici on retire en plus tout caractère invisible ou de
 * contrôle (utilisés pour maquiller des liens / noms) :
 * - contrôles C0 et C1 (\p{Cc}), sauf tabulation et retour à la ligne ;
 * - caractères de format (\p{Cf}) : bidi, largeur nulle, tags Unicode, trait d'union conditionnel… ;
 * - usage privé (\p{Co}) et remplissages invisibles (Hangul, Mongol, Khmer).
 * Les séparateurs de ligne Unicode deviennent de vrais retours à la ligne.
 */
object TextSanitizer {
    // Listé explicitement (pas d'intersection d'ensembles : même sens sous Java et ICU/Android).
    private val forbidden = Regex(
        "[\\u0000-\\u0008\\u000B-\\u001F\\u007F-\\u009F\\p{Cf}\\p{Co}" +
            "\\u115F\\u1160\\u17B4\\u17B5\\u180B-\\u180F\\u3164\\uFFA0]"
    )
    private val lineSeparators = Regex("\\r\\n|[\\r\\u0085\\u2028\\u2029]")
    private val manyBlankLines = Regex("\\n{3,}")

    fun clean(raw: String, maxLength: Int): String {
        val cleaned = Normalizer.normalize(raw, Normalizer.Form.NFC)
            .replace(lineSeparators, "\n")
            .replace(forbidden, "")
            .replace(manyBlankLines, "\n\n")
            .trim()
        if (cleaned.length <= maxLength) return cleaned
        // Coupe sur une frontière de caractère : jamais une moitié d'emoji (paire de substitution).
        var cut = maxLength - 1
        if (cut > 0 && Character.isHighSurrogate(cleaned[cut - 1])) cut--
        return cleaned.substring(0, cut).trimEnd() + "…"
    }
}
