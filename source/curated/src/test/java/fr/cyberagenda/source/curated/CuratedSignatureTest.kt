package fr.cyberagenda.source.curated

import fr.cyberagenda.core.data.SignatureException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CuratedSignatureTest {
    private fun res(name: String) = javaClass.classLoader!!.getResourceAsStream(name).use { it.readBytes() }
    private val content = res("events.json")
    private val signature = res("events.json.sig")

    @Test fun validSignatureAccepted() = CuratedSource.verify(content, signature)

    @Test fun tamperedContentRejected() {
        val tampered = content.toString(Charsets.UTF_8).replace("https://ph0wn.org/", "https://evil.example/").toByteArray()
        assertThrows(SignatureException::class.java) { CuratedSource.verify(tampered, signature) }
    }

    @Test fun garbageSignatureRejected() {
        assertThrows(SignatureException::class.java) { CuratedSource.verify(content, "pas-du-base64!".toByteArray()) }
        assertThrows(SignatureException::class.java) { CuratedSource.verify(content, ByteArray(88) { 'A'.code.toByte() }) }
    }

    @Test fun mapsDateOnlyEvents() {
        val e = CuratedMapper.map(CuratedEvent(id = "x", title = "T", start = "2027-03-12", end = "2027-03-13", url = "https://a.org"), "selection")!!
        assertEquals("selection:x", e.id)
        assertEquals(2 * 24 * 3600 * 1000L - 1, e.endsAtEpochMs - e.startsAtEpochMs)
    }

    @Test fun rejectsUnsafeId() =
        assertEquals(null, CuratedMapper.map(CuratedEvent(id = "../x", title = "T", start = "2027-03-12", url = "https://a.org"), "selection"))
}
