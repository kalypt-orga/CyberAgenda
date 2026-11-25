package fr.cyberagenda.source.openagenda

import fr.cyberagenda.core.model.EventFormat
import fr.cyberagenda.core.model.EventKind
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class OpenAgendaMapperTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
    private val now = OffsetDateTime.parse("2026-11-25T12:00:00+01:00").toInstant().toEpochMilli()

    private fun event(extra: String = "", title: String = "Atelier : protéger ses mots de passe", status: Int = 1, mode: Int = 1) = json.decodeFromString<OaEvent>(
        """
        {"uid": 42, "title": "$title", "description": "Initiation pour débutants",
         "longDescription": "Venez découvrir les [gestionnaires](https://exemple.org) de mots de passe.",
         "status": $status, "attendanceMode": $mode,
         "timings": [
           {"begin": "2026-11-01T14:00:00+01:00", "end": "2026-11-01T16:00:00+01:00"},
           {"begin": "2026-12-02T14:00:00+01:00", "end": "2026-12-02T16:00:00+01:00"},
           {"begin": "2026-12-09T14:00:00+01:00", "end": "2026-12-09T16:00:00+01:00"}
         ],
         "location": {"name": "Médiathèque", "city": "Brest", "postalCode": "29200", "latitude": 48.39, "longitude": -4.48},
         "registration": [{"type": "email", "value": "x@exemple.org"}, {"type": "link", "value": "https://exemple.org/inscription"}],
         "canonicalUrl": "https://openagenda.com/agenda/events/atelier",
         "originAgenda": {"title": "Médiathèques de Brest"},
         "rights": {"license": {"name": "Licence Ouverte 2.0"}, "licensor": {"name": "Ville de Brest"}},
         "updatedAt": "2026-11-20T09:15:00.000Z"
         $extra}
        """.trimIndent()
    )

    @Test fun oneEventPerUpcomingSession() {
        val out = OpenAgendaMapper.map(event(), "openagenda", now)!!
        assertEquals(2, out.size) // la séance du 1er novembre est passée
        assertEquals("openagenda:42-" + out[0].startsAtEpochMs, out[0].id)
    }

    @Test fun fields() {
        val e = OpenAgendaMapper.map(event(), "openagenda", now)!!.first()
        assertEquals(EventKind.WORKSHOP, e.kind)
        assertEquals(EventFormat.ONSITE, e.format)
        assertEquals("Médiathèque, Brest (29)", e.location)
        assertEquals("https://exemple.org/inscription", e.url)
        assertEquals("Médiathèques de Brest", e.organizer)
        assertTrue(e.beginnerFriendly)
    }

    @Test fun licenseIsCredited() {
        val d = OpenAgendaMapper.map(event(), "openagenda", now)!!.first().description
        assertTrue(d, d.contains("Source : Ville de Brest via OpenAgenda — Licence Ouverte 2.0 — mis à jour le 20/11/2026"))
        assertFalse("lien markdown retiré", d.contains("](https"))
    }

    @Test fun cancelledIgnored() = assertNull(OpenAgendaMapper.map(event(status = 6), "openagenda", now))

    @Test fun onlineHasNoPlace() {
        val e = OpenAgendaMapper.map(event(mode = 2), "openagenda", now)!!.first()
        assertEquals(EventFormat.ONLINE, e.format)
        assertNull(e.location)
        assertNull(e.latitude)
    }

    @Test fun cyberFilter() {
        assertTrue(OpenAgendaMapper.isCyber(event()))
        assertTrue(OpenAgendaMapper.isCyber(event(title = "NIS 2 : votre entreprise est-elle concernée ?")))
        val offTopic = json.decodeFromString<OaEvent>("""{"uid": 1, "title": "Hackathon climat", "description": "Coder pour la planète"}""")
        assertFalse(OpenAgendaMapper.isCyber(offTopic))
    }

    @Test fun passingMentionIsNotEnough() {
        val one = json.decodeFromString<OaEvent>("""{"uid": 2, "title": "Expérimentez l'IA", "longDescription": "Souveraineté numérique et protection des données"}""")
        val two = json.decodeFromString<OaEvent>("""{"uid": 3, "title": "Communiquer en confidentialité", "longDescription": "À l'heure où la cybersécurité, la protection des données…"}""")
        assertFalse(OpenAgendaMapper.isCyber(one))
        assertTrue(OpenAgendaMapper.isCyber(two))
    }

    @Test fun placeNamesAreNotCyber() {
        val base = json.decodeFromString<OaEvent>("""{"uid": 4, "title": "ATELIER EXCEL - CYBERBASE BELLICART"}""")
        val good = json.decodeFromString<OaEvent>("""{"uid": 5, "title": "ATELIER CYBER-SECURITE - CYBERBASE BELLICART"}""")
        val sms = json.decodeFromString<OaEvent>("""{"uid": 6, "title": "Le point sur : les arnaques et attaques informatiques"}""")
        assertFalse(OpenAgendaMapper.isCyber(base))
        assertTrue(OpenAgendaMapper.isCyber(good))
        assertTrue(OpenAgendaMapper.isCyber(sms))
    }

    @Test fun aggregatedCopiesShareKey() {
        val a = json.decodeFromString<OaEvent>("""{"uid": 7, "title": "Salon des Masters – Cybersécurité", "timings": [{"begin": "2026-12-01T10:00:00+01:00", "end": "2026-12-01T18:00:00+01:00"}]}""")
        val b = a.copy(uid = 8, title = "Salon des masters - cybersécurité")
        assertEquals(OpenAgendaMapper.sameEventKey(a), OpenAgendaMapper.sameEventKey(b))
    }

    @Test fun summaryNotRepeated() {
        val e = json.decodeFromString<OaEvent>("""{"uid": 9, "title": "Atelier cybersécurité", "description": "Les cyberattaques se multiplient.",
            "longDescription": "Les cyberattaques se multiplient. Venez découvrir les métiers.",
            "timings": [{"begin": "2026-12-01T10:00:00+01:00", "end": "2026-12-01T12:00:00+01:00"}], "canonicalUrl": "https://openagenda.com/x"}""")
        val d = OpenAgendaMapper.map(e, "openagenda", now)!!.first().description
        assertEquals(1, Regex("se multiplient").findAll(d).count())
    }

    @Test fun markdownToPlainText() {
        assertEquals("Titre\n• un\n• deux\nRepartez avec les 10 règles.",
            OpenAgendaMapper.plainText("## Titre\n*   un\n- deux\n**Repartez avec les 10 règles.**"))
    }

    @Test fun nestedTitleDuplicatesMerged() {
        fun ev(uid: Long, title: String, city: String) = json.decodeFromString<OaEvent>("""{"uid": $uid, "title": "$title",
            "location": {"city": "$city"}, "timings": [{"begin": "2026-12-08T09:00:00+01:00", "end": "2026-12-08T13:00:00+01:00"}]}""")
        val out = OpenAgendaMapper.dropNestedDuplicates(listOf(
            ev(1, "Futur Connect", "Roubaix"), ev(2, "Futur Connect - Forum Cyber, IA & Tech", "Roubaix"), ev(3, "Futur Connect", "Lille"),
        ))
        assertEquals(listOf(2L, 3L), out.map { it.uid }.sorted())
    }

    @Test fun salonIsConference() {
        val e = event(title = "Salon Formations et Métiers de la Défense & Cybersécurité")
        assertEquals(EventKind.CONFERENCE, OpenAgendaMapper.map(e, "openagenda", now)!!.first().kind)
    }

    @Test fun sameSlotMentionedInDescriptionMerged() {
        fun ev(uid: Long, title: String, desc: String) = json.decodeFromString<OaEvent>("""{"uid": $uid, "title": "$title", "description": "$desc",
            "location": {"city": "Roubaix"}, "timings": [{"begin": "2026-12-08T09:00:00+01:00", "end": "2026-12-08T13:00:00+01:00"}]}""")
        val out = OpenAgendaMapper.dropNestedDuplicates(listOf(
            ev(1, "Futur Connect - Forum Cyber, IA & Tech du 8 décembre", "Le forum revient"),
            ev(2, "Forum Cyber et Tech", "Forum FUTUR CONNECT : rencontrez des entreprises"),
            ev(3, "Atelier mots de passe", "Bien choisir ses mots de passe"),
        ))
        assertEquals(listOf(1L, 3L), out.map { it.uid }.sorted())
    }

    @Test fun publisherCutIsFlagged() {
        val text = ("Découvrir les métiers de la cybersécurité ".repeat(20)).take(497) + "com"
        val e = json.decodeFromString<OaEvent>("""{"uid": 10, "title": "Atelier cybersécurité", "longDescription": "$text",
            "timings": [{"begin": "2026-12-01T10:00:00+01:00", "end": "2026-12-01T12:00:00+01:00"}], "canonicalUrl": "https://openagenda.com/x"}""")
        val d = OpenAgendaMapper.map(e, "openagenda", now)!!.first().description
        assertTrue(d, d.contains("com…\n\n" + OpenAgendaMapper.CUT_NOTICE))
    }
}
