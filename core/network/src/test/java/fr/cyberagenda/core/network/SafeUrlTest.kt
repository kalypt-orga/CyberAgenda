package fr.cyberagenda.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeUrlTest {
    @Test fun acceptsHttps() = assertTrue(SafeUrl.isHttps("https://ctftime.org/event/1/"))
    @Test fun rejectsHttp() = assertFalse(SafeUrl.isHttps("http://ctftime.org/"))
    @Test fun rejectsJavascript() = assertFalse(SafeUrl.isHttps("javascript:alert(1)"))
    @Test fun rejectsIntentScheme() = assertFalse(SafeUrl.isHttps("intent://x#Intent;end"))
    @Test fun rejectsUserInfo() = assertFalse(SafeUrl.isHttps("https://ctftime.org@evil.example/"))
    @Test fun rejectsBlank() = assertFalse(SafeUrl.isHttps("  "))
    @Test fun rejectsSingleLabelHost() = assertFalse(SafeUrl.isHttps("https://localhost/"))
    @Test fun homographShownAsPunycode() =
        assertEquals("xn--ctftme-svf.org", SafeUrl.displayHost("https://ctftіme.org/")) // "і" cyrillique
}
