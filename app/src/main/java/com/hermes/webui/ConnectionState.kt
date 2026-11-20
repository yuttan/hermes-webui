package com.hermes.webui

import java.net.HttpURLConnection
import java.net.URL

/** Hermes reachability shown by the antenna icon. */
enum class ConnectionState { CHECKING, CONNECTED, DISCONNECTED }

object HermesProbe {
    private const val CONNECT_TIMEOUT_MS = 2500
    private const val READ_TIMEOUT_MS = 3000

    /** True only for a Hermes dashboard /api/status answer (public, no login needed). */
    fun isHermesStatus(code: Int, body: String): Boolean =
        code == 200 && body.contains("\"gateway_state\"") && body.contains("\"version\"")

    /** Blocking probe of `<baseUrl>/api/status`; call off the main thread. */
    fun check(baseUrl: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("$baseUrl/api/status").openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                useCaches = false
            }
            val code = conn.responseCode
            val body = if (code == 200) conn.inputStream.use { input ->
                val buf = ByteArray(8192)
                var total = 0
                while (total < buf.size) {
                    val n = input.read(buf, total, buf.size - total)
                    if (n < 0) break
                    total += n
                }
                String(buf, 0, total, Charsets.UTF_8)
            } else ""
            isHermesStatus(code, body)
        } catch (e: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }
}
