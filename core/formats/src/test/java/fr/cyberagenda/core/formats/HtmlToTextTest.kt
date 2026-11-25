package fr.cyberagenda.core.formats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlToTextTest {
    @Test fun stripsTagsAndKeepsStructure() {
        assertEquals("Titre\nUn paragraphe.\n• un\n• deux",
            HtmlToText.convert("<h3>Titre</h3><p>Un <b>paragraphe</b>.</p><ul><li>un</li><li>deux</li></ul>"))
    }

    @Test fun removesScripts() {
        val out = HtmlToText.convert("<p>ok</p><script>alert('x')</script><style>p{}</style>")
        assertEquals("ok", out)
    }

    @Test fun decodesEntities() =
        assertEquals("Café & « vie privée »", HtmlToText.convert("Caf&eacute; &amp; &laquo;&nbsp;vie priv&#233;e&nbsp;&raquo;"))

    @Test fun dropsControlCharacterEntities() =
        assertFalse(HtmlToText.convert("a&#0;b&#x202E;c").contains('\u0000'))

    // --- Garde-fous de l'audit (DATA-02) : temps linéaire sur des descriptions piégées ---
    private fun fast(html: String) {
        val t0 = System.nanoTime()
        HtmlToText.convert(html)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("trop lent : $ms ms", ms < 200)
    }

    @Test fun unclosedScriptsAreFast() = fast("<script>a".repeat(6_250))
    @Test fun unclosedStylesAreFast() = fast("<style x>".repeat(5_555))
    @Test fun manyOpeningBracketsAreFast() = fast("<".repeat(50_000))
    @Test fun unclosedListItemsAreFast() = fast("<li ".repeat(12_500))

    @Test fun unclosedScriptDropsTheRest() = assertEquals("avant", HtmlToText.convert("avant<script>alert(1) <p>après</p>"))
    @Test fun scriptCaseInsensitive() = assertEquals("ab", HtmlToText.convert("a<SCRIPT type=x>z</ScRiPt >b"))
}
