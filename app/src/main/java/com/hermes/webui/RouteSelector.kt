package com.hermes.webui

/** User-selected connection mode. AUTO picks LAN on home Wi-Fi, otherwise Tailscale. */
enum class Mode { AUTO, LAN, VPN;
    companion object {
        fun parse(value: String?): Mode = values().firstOrNull { it.name == value } ?: AUTO
    }
}

/** Route actually in use. NONE = neither endpoint answered. */
enum class Route { LAN, VPN, NONE }

object RouteSelector {
    /**
     * AUTO: LAN only when a Wi-Fi/Ethernet network is up and the Hermes server answers on LAN;
     * otherwise Tailscale if it answers; otherwise NONE.
     * Manual modes always use the chosen route (reachability is only reported).
     */
    fun select(mode: Mode, localNetwork: Boolean, lanOk: () -> Boolean, vpnOk: () -> Boolean): Route =
        when (mode) {
            Mode.LAN -> Route.LAN
            Mode.VPN -> Route.VPN
            Mode.AUTO -> when {
                localNetwork && lanOk() -> Route.LAN
                vpnOk() -> Route.VPN
                else -> Route.NONE
            }
        }

    /** True only for a Hermes dashboard /api/status answer, not any device on a foreign 192.168.1.x. */
    fun isHermesStatus(code: Int, body: String): Boolean =
        code == 200 && body.contains("\"gateway_state\"") && body.contains("\"version\"")
}
