package com.hermes.webui

import org.junit.Assert.*
import org.junit.Test

class ServerUrlTest {
    private fun ok(input: String) = ServerUrl.parse(input) as? ServerUrl.Result.Ok
        ?: throw AssertionError("expected Ok for '$input' but got ${ServerUrl.parse(input)}")
    private fun err(input: String) = (ServerUrl.parse(input) as? ServerUrl.Result.Error)?.reason

    @Test fun normalizesSchemeHostPortAndSlash() {
        assertEquals("http://100.64.0.1:9119", ok("http://100.64.0.1:9119/").url)
        assertEquals("https://pc.tail1.ts.net", ok("HTTPS://PC.Tail1.TS.NET/").url)
        assertEquals("https://pc.tail1.ts.net", ok("https://pc.tail1.ts.net:443").url)
        assertEquals("http://192.168.1.10", ok("http://192.168.1.10:80").url)
        assertEquals("https://example.com/hermes", ok("https://example.com/hermes/").url)
    }

    @Test fun bareInputGetsSchemeByHost() {
        assertEquals("https://pc.tail1.ts.net", ok("pc.tail1.ts.net").url)
        assertEquals("http://100.80.1.2:9119", ok("100.80.1.2:9119").url)
        assertEquals("http://mypc:9119", ok(" mypc:9119 ").url)
    }

    @Test fun foldsFullWidthInputFromCjkKeyboards() {
        assertEquals("http://100.124.1.25:8899", ok("１００．１２４．１．２５：８８９９").url)
        assertEquals("https://pc.tail1.ts.net", ok("ｐｃ。ｔａｉｌ１。ｔｓ。ｎｅｔ").url)
        assertEquals("http://192.168.1.10:9119", ok("ｈｔｔｐ：／／１９２．１６８．１．１０：９１１９　").url)
    }

    @Test fun rejectsBadInput() {
        assertEquals(ServerUrl.Reason.EMPTY, err("   "))
        assertEquals(ServerUrl.Reason.BAD_SCHEME, err("ftp://example.com"))
        assertEquals(ServerUrl.Reason.BAD_SCHEME, err("javascript://x"))
        assertEquals(ServerUrl.Reason.CREDENTIALS, err("http://user:pw@100.64.0.1"))
        assertEquals(ServerUrl.Reason.QUERY, err("http://100.64.0.1/?a=1"))
        assertEquals(ServerUrl.Reason.QUERY, err("http://100.64.0.1/#x"))
        assertEquals(ServerUrl.Reason.BAD_PORT, err("http://100.64.0.1:0"))
        assertEquals(ServerUrl.Reason.BAD_PORT, err("http://100.64.0.1:"))
        assertEquals(ServerUrl.Reason.BAD_PORT, err("http://100.64.0.1:70000"))
        assertNotNull(err("http://100.64.0.1:abc"))
        assertNotNull(err("http://"))
        assertNotNull(err("http://a b"))
        assertNotNull(err("http://x\"><script>"))
    }

    @Test fun warnsOnlyForCleartextToPublicHosts() {
        assertNull(ok("http://100.64.0.1:9119").warning)
        assertNull(ok("http://100.127.255.1").warning)
        assertNull(ok("http://192.168.1.10:9119").warning)
        assertNull(ok("http://10.0.0.5").warning)
        assertNull(ok("http://172.16.0.1").warning)
        assertNull(ok("http://172.31.0.1").warning)
        assertNull(ok("http://127.0.0.1:9119").warning)
        assertNull(ok("http://localhost:9119").warning)
        assertNull(ok("http://mypc:9119").warning)
        assertNull(ok("http://nas.local").warning)
        assertNull(ok("http://pc.tail1.ts.net").warning)
        assertNull(ok("http://[fd7a:115c:a1e0::1]:9119").warning)
        assertNull(ok("https://hermes.example.com").warning)

        val w = ServerUrl.Warning.CLEARTEXT_PUBLIC
        assertEquals(w, ok("http://hermes.example.com").warning)
        assertEquals(w, ok("http://203.0.113.5:9119").warning)
        assertEquals(w, ok("http://100.128.0.1").warning)
        assertEquals(w, ok("http://172.32.0.1").warning)
        assertEquals(w, ok("http://ts.net.evil.example").warning)
        assertEquals(w, ok("http://192.168.1.1.evil.example").warning)
        assertEquals(w, ok("http://010.0.0.1").warning)
    }
}
