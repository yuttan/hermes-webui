package com.hermes.webui

import org.junit.Assert.*
import org.junit.Test

class PtyTokenTest {
    @Test fun originOfNormalisedServerUrls() {
        assertEquals("http://100.124.137.25:8899", PtyToken.origin("http://100.124.137.25:8899"))
        assertEquals("https://pc.tail1.ts.net", PtyToken.origin("https://pc.tail1.ts.net/hermes"))
        assertEquals("http://[fd7a::1]:9119", PtyToken.origin("http://[fd7a::1]:9119"))
        assertNull(PtyToken.origin("not a url"))
    }

    @Test fun onlyHexTokensAreAccepted() {
        assertTrue(PtyToken.isValid("0123456789abcdef0123456789abcdef"))
        assertFalse(PtyToken.isValid(null))
        assertFalse(PtyToken.isValid("short"))
        assertFalse(PtyToken.isValid("0123456789ABCDEF0123456789ABCDEF"))
        assertFalse(PtyToken.isValid("'+alert(1)+'0123456789abcdef"))
    }

    @Test fun scriptRestoresSavedTokenAndNeverEmbedsUntrustedText() {
        val t = "0123456789abcdef0123456789abcdef"
        val s = PtyToken.script(t)
        assertTrue(s.contains("saved = '$t'"))
        assertTrue(s.contains(PtyToken.STORAGE_KEY))
        assertTrue(s.contains(PtyToken.BRIDGE))
        assertTrue(PtyToken.script("x'); evil();//").contains("saved = ''"))
        assertTrue(PtyToken.script(null).contains("saved = ''"))
    }
}
