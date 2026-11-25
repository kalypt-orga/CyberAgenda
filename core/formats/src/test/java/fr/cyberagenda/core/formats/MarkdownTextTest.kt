package fr.cyberagenda.core.formats

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTextTest {
    @Test fun headingsBulletsBoldLinks() = assertEquals(
        "Titre\n• un\n• deux\nVoir le site.",
        MarkdownText.plain("## Titre\n*   un\n- deux\n**Voir [le site](https://exemple.org).**"),
    )

    @Test fun boldAcrossLines() = assertEquals(
        "Prochaines séances :\nmardi 10 h",
        MarkdownText.plain("**Prochaines séances :\nmardi 10 h**"),
    )

    @Test fun horizontalRuleRemoved() = assertEquals("Avant\n\nAprès", MarkdownText.plain("Avant\n\n***\n\nAprès"))

    @Test fun plainTextUntouched() = assertEquals("2 * 3 = 6 - ok", MarkdownText.plain("2 * 3 = 6 - ok"))

    @Test fun linkWithTitleAndItalics() = assertEquals(
        "Par l'Association STS et les Éditions du Cavalier Bleu.",
        MarkdownText.plain("Par _l'[Association STS](https://asts.paris/ \"Ouverture dans un nouvel onglet\")_ et *les Éditions du Cavalier Bleu*."),
    )

    @Test fun snakeCaseUntouched() = assertEquals("variable mon_nom_de_variable", MarkdownText.plain("variable mon_nom_de_variable"))

    @Test fun whitespaceOnlyLinesCollapsed() = assertEquals("Replay en ligne\n\nImage : Jcomp", MarkdownText.plain("Replay en ligne  \n  \n  \n  \nImage : Jcomp"))
}
