package com.hermes.webui

import android.content.Context
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.net.URI
import java.util.Locale

/**
 * Keeps the dashboard chat's PTY "attach token" across app restarts.
 *
 * The dashboard keeps a chat's terminal (and its Hermes session) alive for a while after the page goes
 * away and re-attaches it when the page comes back with the same token. The token lives in
 * sessionStorage (`hermes.pty.token.chat`), which a WebView loses whenever Android kills the app.
 * Without it, opening Chat again spawns a new terminal, and every new terminal is a new, empty session.
 * This class saves the token on the device and puts it back before the dashboard's scripts run.
 */
object PtyToken {
    const val STORAGE_KEY = "hermes.pty.token.chat"
    const val BRIDGE = "HermesRemoteToken"

    /** The dashboard mints 16 random bytes as hex; accept only hex so nothing else reaches the script. */
    fun isValid(token: String?): Boolean =
        token != null && token.length in 16..128 && token.all { it in '0'..'9' || it in 'a'..'f' }

    /** `scheme://host[:port]` of a normalised server URL, as WebView origin rules expect. */
    fun origin(serverUrl: String): String? {
        val u = try { URI(serverUrl) } catch (e: Exception) { return null }
        val scheme = u.scheme?.lowercase(Locale.ROOT) ?: return null
        val host = u.host ?: return null
        val port = if (u.port == -1) "" else ":${u.port}"
        return "$scheme://${host.lowercase(Locale.ROOT)}$port"
    }

    /** Document-start script: restore the saved token, and report every new token to the app. */
    fun script(saved: String?): String {
        val literal = if (isValid(saved)) "'$saved'" else "''"
        return """
            (function () {
              try {
                var K = '$STORAGE_KEY', s = window.sessionStorage, saved = $literal;
                if (saved && !s.getItem(K)) s.setItem(K, saved);
                var report = function (v) {
                  try { if (window.$BRIDGE) window.$BRIDGE.postMessage(String(v)); } catch (e) {}
                };
                var cur = s.getItem(K);
                if (cur && cur !== saved) report(cur);
                var orig = Storage.prototype.setItem;
                Storage.prototype.setItem = function (k, v) {
                  orig.apply(this, arguments);
                  if (k === K && this === window.sessionStorage) report(v);
                };
              } catch (e) {}
            })();
        """.trimIndent()
    }
}

class PtyTokenKeeper(private val context: Context, private val webView: WebView) {
    private var script: ScriptHandler? = null
    private var listenerOrigin: String? = null

    private val prefs get() = context.getSharedPreferences("pty_token", Context.MODE_PRIVATE)

    /** Call on start and whenever the server address changes, before loading the dashboard. */
    fun install(serverUrl: String) {
        val origin = PtyToken.origin(serverUrl) ?: return
        val supported = try {
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) &&
                WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
        } catch (e: RuntimeException) {
            false
        }
        // Old WebView: the dashboard still works, a restarted app just starts a fresh chat terminal.
        if (!supported) return
        if (listenerOrigin != origin) {
            if (listenerOrigin != null) WebViewCompat.removeWebMessageListener(webView, PtyToken.BRIDGE)
            // Only pages of the configured server can report a token (not the OAuth pages, for example).
            WebViewCompat.addWebMessageListener(webView, PtyToken.BRIDGE, setOf(origin)) { _, message, source, isMainFrame, _ ->
                val token = message.data
                if (isMainFrame && source.toString() == origin && PtyToken.isValid(token)) save(origin, token!!)
            }
            listenerOrigin = origin
        }
        register(origin, prefs.getString(origin, null))
    }

    private fun save(origin: String, token: String) {
        if (prefs.getString(origin, null) == token) return
        prefs.edit().putString(origin, token).apply()
        register(origin, token)
    }

    private fun register(origin: String, token: String?) {
        script?.remove()
        script = WebViewCompat.addDocumentStartJavaScript(webView, PtyToken.script(token), setOf(origin))
    }
}
