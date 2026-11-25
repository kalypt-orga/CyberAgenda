package fr.cyberagenda.feature.settings

import fr.cyberagenda.core.model.EventFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.time.LocalDate
import java.time.LocalTime

class ProposalTest {
    private val today = LocalDate.of(2026, 11, 23)
    private val valid = Proposal(
        title = "Café vie privée", start = LocalDate.of(2026, 12, 5), location = "Bordeaux (33)",
        url = "https://exemple.org/cafe",
    )

    @Test fun validProposal() = assertEquals(emptyMap<String, String>(), valid.errors(today))
    @Test fun httpRejected() = assertTrue("url" in valid.copy(url = "http://exemple.org").errors(today))
    @Test fun pastDateRejected() = assertTrue("start" in valid.copy(start = LocalDate.of(2026, 1, 1)).errors(today))
    @Test fun endBeforeStart() = assertTrue("end" in valid.copy(end = LocalDate.of(2026, 12, 1)).errors(today))
    @Test fun onlineNeedsNoLocation() = assertFalse("location" in valid.copy(format = EventFormat.ONLINE, location = "").errors(today))

    @Test fun jsonIsEscapedAndSlugged() {
        val json = valid.copy(title = "Atelier \"Tor\" & VPN", description = "ligne‮ cachée").toCuratedJson()
        assertTrue(json.contains("\"id\": \"atelier-tor-vpn-2026\""))
        assertTrue(json.contains("\\\"Tor\\\""))     // guillemets échappés
        assertFalse(json.contains("‮"))           // caractère bidi supprimé
    }

    // --- Horaires, tarif, issue GitHub ---
    @Test fun dateOnlyStaysAllDay() = assertTrue(valid.toCuratedJson().contains("\"start\": \"2026-12-05\""))
    @Test fun timesUseParisOffset() {
        val json = valid.copy(startTime = LocalTime.of(18, 30), endTime = LocalTime.of(20, 0)).toCuratedJson()
        assertTrue(json, json.contains("\"start\": \"2026-12-05T18:30:00+01:00\""))
        assertTrue(json, json.contains("\"end\": \"2026-12-05T20:00:00+01:00\""))
    }
    @Test fun startTimeOnlyGivesInstant() =
        assertTrue(valid.copy(startTime = LocalTime.of(18, 30)).toCuratedJson().contains("\"end\": \"2026-12-05T18:30:00+01:00\""))
    @Test fun endTimeNeedsStartTime() = assertTrue("startTime" in valid.copy(endTime = LocalTime.of(20, 0)).errors(today))
    @Test fun endTimeAfterStart() =
        assertTrue("endTime" in valid.copy(startTime = LocalTime.of(20, 0), endTime = LocalTime.of(18, 0)).errors(today))
    @Test fun freePrice() {
        val json = valid.copy(price = PriceChoice.FREE).toCuratedJson()
        assertTrue(json.contains("\"free\": true") && json.contains("\"price\": \"Gratuit\""))
    }
    @Test fun paidNeedsAmount() = assertTrue("priceText" in valid.copy(price = PriceChoice.PAID).errors(today))
    @Test fun unknownPriceOmitted() = assertFalse(valid.toCuratedJson().contains("price"))

    @Test fun gitHubUrlPrefillsTemplateFields() {
        val url = valid.copy(organizer = "Asso & Co", price = PriceChoice.FREE).toGitHubIssueUrl("kalypt-orga/CyberAgenda")
        assertTrue(url.startsWith("https://github.com/kalypt-orga/CyberAgenda/issues/new?template=proposer-evenement.yml&"))
        val params = url.substringAfter('?').split('&').associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8) }
        assertEquals("Café vie privée", params["titre"])
        assertEquals("Asso & Co", params["organisation"])        // « & » encodé, pas de paramètre cassé
        assertEquals("Gratuit", params["tarif"])
        assertTrue(params["entree"]!!.contains("\"id\": \"cafe-vie-privee-2026\""))
    }
    @Test fun gitHubUrlStaysShortWithLongDescription() =
        assertTrue(valid.copy(description = "é".repeat(1_000)).toGitHubIssueUrl("kalypt-orga/CyberAgenda").length <= Proposal.MAX_URL)
}
