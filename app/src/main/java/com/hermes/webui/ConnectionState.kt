package com.hermes.webui

/** Hermes reachability shown by the antenna icon. */
enum class ConnectionState { CHECKING, CONNECTED, DISCONNECTED }

object HermesProbe {
    /** True only for a Hermes dashboard /api/status answer (public, no login needed). */
    fun isHermesStatus(code: Int, body: String): Boolean =
        code == 200 && body.contains("\"gateway_state\"") && body.contains("\"version\"")
}
