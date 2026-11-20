package com.hermes.webui

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * Validation of the user-entered dashboard address. Pure (no Android APIs) so it is unit-tested on the JVM.
 *
 * The result is the base URL the WebView opens and `/api/status` is appended to:
 * scheme + host (+ port) (+ path prefix for dashboards behind a reverse proxy), never a trailing slash.
 */
object ServerUrl {

    sealed class Result {
        data class Ok(val url: String, val warning: Warning?) : Result()
        data class Error(val reason: Reason) : Result()
    }

    enum class Reason { EMPTY, BAD_SCHEME, NO_HOST, BAD_PORT, CREDENTIALS, QUERY, MALFORMED }

    /** Plain HTTP to a host that does not look like a home network / VPN address. */
    enum class Warning { CLEARTEXT_PUBLIC }

    fun parse(input: String): Result {
        var text = input.trim()
        if (text.isEmpty()) return Result.Error(Reason.EMPTY)
        if (text.any { it.isWhitespace() || it == '"' || it == '<' || it == '>' }) return Result.Error(Reason.MALFORMED)
        if (!text.contains("://")) {
            // Tailscale Serve names are HTTPS-only; everything else typed bare is a LAN/VPN address on plain HTTP.
            val hostPart = text.substringBefore('/').substringBefore(':').lowercase(Locale.ROOT)
            text = (if (hostPart.endsWith(".ts.net")) "https://" else "http://") + text
        }
        val uri = try { URI(text) } catch (e: URISyntaxException) { return Result.Error(Reason.MALFORMED) }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return Result.Error(Reason.BAD_SCHEME)
        if (uri.rawUserInfo != null) return Result.Error(Reason.CREDENTIALS)
        if (uri.rawQuery != null || uri.rawFragment != null) return Result.Error(Reason.QUERY)
        val rawHost = uri.host ?: return Result.Error(if (uri.rawAuthority.isNullOrEmpty()) Reason.NO_HOST else Reason.MALFORMED)
        if (rawHost.isEmpty()) return Result.Error(Reason.NO_HOST)
        val host = rawHost.lowercase(Locale.ROOT)
        val port = uri.port
        if (port == 0 || port > 65535) return Result.Error(Reason.BAD_PORT)
        // URI accepts "host:" (empty port) silently; treat it as a typo rather than guessing.
        if (port == -1 && uri.rawAuthority.endsWith(":")) return Result.Error(Reason.BAD_PORT)
        val defaultPort = if (scheme == "https") 443 else 80
        val portPart = if (port == -1 || port == defaultPort) "" else ":$port"
        val path = (uri.rawPath ?: "").trimEnd('/')
        val url = "$scheme://$host$portPart$path"
        val warning = if (scheme == "http" && !isPrivateHost(host)) Warning.CLEARTEXT_PUBLIC else null
        return Result.Ok(url, warning)
    }

    /**
     * Home LAN, Tailscale (CGNAT range and MagicDNS) or loopback. Lookalikes such as
     * `ts.net.example.com` or `192.168.1.1.example.com` are not private.
     */
    fun isPrivateHost(host: String): Boolean {
        val h = host.lowercase(Locale.ROOT).trimEnd('.')
        if (h == "localhost" || h.endsWith(".local") || h.endsWith(".ts.net") || h.endsWith(".home.arpa")) return true
        if (h.startsWith("[")) {
            val v6 = h.trim('[', ']')
            return v6 == "::1" || v6.startsWith("fd") || v6.startsWith("fc") || v6.startsWith("fe80")
        }
        val ipv4 = parseIpv4(h)
        if (ipv4 != null) {
            val (a, b) = ipv4
            return a == 10 || a == 127 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 100 && b in 64..127)
        }
        // A single label ("myserver") is a LAN or MagicDNS short name, never a public DNS name.
        return !h.contains('.')
    }

    private fun parseIpv4(h: String): Pair<Int, Int>? {
        val parts = h.split('.')
        if (parts.size != 4) return null
        val nums = parts.map { p ->
            // Leading zeros may be read as octal by some resolvers; do not vouch for such an address.
            if (p.isEmpty() || p.length > 3 || !p.all { it in '0'..'9' } || (p.length > 1 && p[0] == '0')) return null
            p.toInt().takeIf { it in 0..255 } ?: return null
        }
        return nums[0] to nums[1]
    }
}
