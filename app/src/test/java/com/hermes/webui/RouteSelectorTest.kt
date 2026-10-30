package com.hermes.webui

import org.junit.Assert.*
import org.junit.Test

class RouteSelectorTest {
    private fun auto(wifi: Boolean, lan: Boolean, vpn: Boolean): Pair<Route, List<String>> {
        val probed = mutableListOf<String>()
        val r = RouteSelector.select(Mode.AUTO, wifi, { probed += "lan"; lan }, { probed += "vpn"; vpn })
        return r to probed
    }

    @Test fun homeWifiPrefersLanWithoutProbingVpn() {
        assertEquals(Route.LAN to listOf("lan"), auto(wifi = true, lan = true, vpn = true))
    }

    @Test fun noWifiSkipsLanProbe() {
        assertEquals(Route.VPN to listOf("vpn"), auto(wifi = false, lan = true, vpn = true))
    }

    @Test fun foreignWifiFallsBackToVpn() {
        assertEquals(Route.VPN to listOf("lan", "vpn"), auto(wifi = true, lan = false, vpn = true))
    }

    @Test fun nothingReachable() {
        assertEquals(Route.NONE, auto(wifi = true, lan = false, vpn = false).first)
        assertEquals(Route.NONE, auto(wifi = false, lan = true, vpn = false).first)
    }

    @Test fun manualModesIgnoreProbes() {
        assertEquals(Route.LAN, RouteSelector.select(Mode.LAN, false, { fail(); false }, { fail(); false }))
        assertEquals(Route.VPN, RouteSelector.select(Mode.VPN, true, { fail(); false }, { fail(); false }))
    }

    @Test fun modeParsing() {
        assertEquals(Mode.AUTO, Mode.parse(null))
        assertEquals(Mode.AUTO, Mode.parse("bogus"))
        assertEquals(Mode.VPN, Mode.parse("VPN"))
    }

    @Test fun onlyHermesStatusCounts() {
        val ok = """{"version":"0.22.5","gateway_state":"running"}"""
        assertTrue(RouteSelector.isHermesStatus(200, ok))
        assertFalse(RouteSelector.isHermesStatus(302, ok))
        assertFalse(RouteSelector.isHermesStatus(200, "<html>router login</html>"))
    }
}
