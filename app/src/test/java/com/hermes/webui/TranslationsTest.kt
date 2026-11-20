package com.hermes.webui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

/** Every translation must cover exactly the translatable English strings (no stale or missing keys). */
class TranslationsTest {
    private val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

    private fun strings(dir: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(dir, "strings.xml"))
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element }
            .filter { it.getAttribute("translatable") != "false" }
            .associate { it.getAttribute("name") to it.textContent }
    }

    @Test fun allLocalesHaveTheSameKeys() {
        val base = strings(File(res, "values"))
        val locales = res.listFiles { f -> f.name.startsWith("values-") && File(f, "strings.xml").exists() }!!
        assertTrue("expected many translations, got ${locales.size}", locales.size >= 10)
        for (dir in locales) {
            val t = strings(dir)
            assertEquals("keys of ${dir.name}", base.keys, t.keys)
            for ((k, v) in t) assertTrue("${dir.name}/$k is empty", v.isNotBlank())
            // Addresses and commands must survive translation unchanged.
            for (token in listOf("https://mypc.tail1234.ts.net", "http://100.x.y.z:9119")) {
                assertTrue("${dir.name}/setup_help keeps $token", t.getValue("setup_help").contains(token))
            }
        }
    }
}
