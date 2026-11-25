package fr.cyberagenda.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cas réels relevés dans les sources (novembre 2026). */
class PriceHintsTest {
    private fun ev(desc: String, price: String? = null, free: Boolean? = null) = Event(
        id = "s:1", sourceId = "s", title = "T", description = desc, startsAtEpochMs = 0, endsAtEpochMs = 1,
        format = EventFormat.ONSITE, kind = EventKind.WORKSHOP, url = "https://a.org", price = price, free = free,
    )
    private fun price(desc: String) = PriceHints.apply(ev(desc)).price

    @Test fun oteriaPrizeDoesNotBlock() = assertEquals("Gratuit",
        price("Durée : 6 heures - Participation : Gratuite - Éligibilité : Étudiants\nRécompenses - 1re place : 500 € et des goodies"))
    @Test fun inscriptionGratuite() = assertEquals("Gratuit", price("Inscription entièrement gratuite - Participation à distance"))
    @Test fun participationIndividuelleEtGratuite() = assertEquals("Gratuit", price("Participation : individuelle et gratuite"))
    @Test fun gratuitEtOuvert() = assertEquals("Gratuit", price("Gratuit et ouvert au public."))
    @Test fun confGratuite() = assertEquals("Gratuit", price("Sécurité & logiciel libre; conf gratuite (EN)"))
    @Test fun englishFree() = assertEquals("Gratuit", price("N0v4CTF 2026 is a free, 24-hour online Jeopardy-style CTF"))
    @Test fun entreeLibre() = assertEquals("Gratuit", price("Entrée libre dans la limite des places"))

    @Test fun softwareFreeIsNotEventFree() = assertNull(price("Ces logiciels sont à la fois libres et gratuits."))
    @Test fun entryCostWins() = assertNull(price("Conférence gratuite pour les étudiants, billet à 20 € sinon"))
    @Test fun pricePerPerson() = assertNull(price("Atelier gratuit pour les membres ; 15 € par personne"))
    @Test fun nothingSaid() = assertNull(price("Conférence sur le pentest"))

    @Test fun sourcePriceKept() = assertEquals("50 €", PriceHints.apply(ev("gratuit", price = "50 €")).price)
    @Test fun freeFlagGivesLabel() = assertEquals("Gratuit", PriceHints.apply(ev("", free = true)).price)

    // --- Garde-fous de l'audit (DATA-06, DATA-02) ---
    @Test fun paidOnSiteIsNotFree() = assertNull(price("Inscription gratuite. Paiement sur place : 40 euros."))
    @Test fun freeThenPaid() = assertNull(price("Inscription gratuite puis 250 € à régler à l'arrivée."))
    @Test fun freeForFirstOnly() = assertNull(price("Le CTF est gratuit pour les 10 premiers inscrits ; ensuite 99 EUR."))
    @Test fun englishFeeAfter() = assertNull(price("It's free to register, then a 300 USD fee applies."))
    @Test fun vipPassRequired() = assertNull(price("Conférence gratuite ! Pass VIP 500 € obligatoire."))
    @Test fun cashPrizeDoesNotBlock() = assertEquals("Gratuit", price("CTF gratuit et ouvert à tous. Cash prize : 1000 €"))

    @Test fun longDigitRunIsFast() {
        val t0 = System.nanoTime()
        price("1".repeat(5_200) + " gratuit")
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 100)
    }
}
