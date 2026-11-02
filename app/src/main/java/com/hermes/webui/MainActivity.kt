package com.hermes.webui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Full-screen WebView for the Hermes dashboard, always reached through Tailscale.
 * The dashboard's Nous OAuth callback and login cookies are bound to this one origin,
 * so there is deliberately no LAN/VPN switching.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        const val HERMES_URL = "http://100.124.137.25:8899"
        private const val CONNECT_TIMEOUT_MS = 2500
        private const val READ_TIMEOUT_MS = 3000
        private const val POLL_MS = 15_000L
        private const val NETWORK_DEBOUNCE_MS = 1_000L
        private val COLOR_CONNECTED = Color.parseColor("#34C759")
        private val COLOR_CHECKING = Color.parseColor("#8E8E93")
        private val COLOR_DISCONNECTED = Color.parseColor("#FF453A")

        @VisibleForTesting internal var testProbe: (() -> Boolean)? = null
        @VisibleForTesting internal var testExecutor: Executor? = null
    }

    private lateinit var webView: WebView
    private lateinit var statusIcon: ImageView
    private lateinit var menuButton: ImageButton
    private lateinit var shortcutPanel: View

    private val main = Handler(Looper.getMainLooper())
    private val ownExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val executor: Executor get() = testExecutor ?: ownExecutor
    private var connectivity: ConnectivityManager? = null
    private var pulse: ObjectAnimator? = null
    private var generation = 0
    private var destroyed = false
    private var pageFailed = false
    private var offlineShown = false

    @VisibleForTesting internal var state = ConnectionState.CHECKING
        private set

    private val checkRunnable = Runnable { checkConnection() }
    private val pollRunnable = object : Runnable {
        override fun run() {
            checkConnection()
            main.postDelayed(this, POLL_MS)
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = scheduleCheck()
        override fun onLost(network: Network) = scheduleCheck()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applyFullscreen()

        webView = findViewById(R.id.webview)
        statusIcon = findViewById(R.id.iv_status)
        menuButton = findViewById(R.id.btn_menu)
        shortcutPanel = findViewById(R.id.shortcut_panel)
        menuButton.setOnClickListener { setPanelOpen(shortcutPanel.visibility != View.VISIBLE) }
        buildChips(findViewById(R.id.flow_keys), Shortcuts.keys)
        buildChips(findViewById(R.id.flow_commands), Shortcuts.commands)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = false

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (url != null && url.startsWith("http")) {
                    pageFailed = false
                    offlineShown = false
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame != true) return
                pageFailed = true
                scheduleCheck(300)
            }
        }

        findViewById<View>(R.id.btn_up).setOnClickListener { keyDispatcher.send(KeyEvent.KEYCODE_DPAD_UP) }
        findViewById<View>(R.id.btn_down).setOnClickListener { keyDispatcher.send(KeyEvent.KEYCODE_DPAD_DOWN) }
        findViewById<View>(R.id.btn_enter).setOnClickListener { keyDispatcher.send(KeyEvent.KEYCODE_ENTER) }
        findViewById<View>(R.id.btn_refresh).setOnClickListener {
            keyDispatcher.cancel()
            if (offlineShown || pageFailed || webView.url.isNullOrEmpty()) webView.loadUrl(HERMES_URL)
            else webView.reload()
            checkConnection()
        }

        showState(ConnectionState.CHECKING)
        webView.loadUrl(HERMES_URL)

        connectivity = getSystemService(ConnectivityManager::class.java)
        try {
            val request = NetworkRequest.Builder()
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            connectivity?.registerNetworkCallback(request, networkCallback)
        } catch (e: RuntimeException) {
            // Best effort; polling still updates the icon.
        }
    }

    private fun scheduleCheck(delayMs: Long = NETWORK_DEBOUNCE_MS) {
        main.removeCallbacks(checkRunnable)
        main.postDelayed(checkRunnable, delayMs)
    }

    /** Probe /api/status off the UI thread; newest result wins. */
    private fun checkConnection() {
        if (destroyed) return
        main.removeCallbacks(checkRunnable)
        val token = ++generation
        if (state != ConnectionState.CONNECTED) showState(ConnectionState.CHECKING)
        executor.execute {
            val ok = probe()
            main.post {
                if (destroyed || token != generation) return@post
                onProbeResult(ok)
            }
        }
    }

    private fun onProbeResult(ok: Boolean) {
        showState(if (ok) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED)
        if (ok && (pageFailed || offlineShown)) {
            // Connection came back: leave the error page automatically.
            webView.loadUrl(HERMES_URL)
        } else if (!ok && pageFailed && !offlineShown) {
            offlineShown = true
            webView.loadDataWithBaseURL(null, offlineHtml(), "text/html", "utf-8", null)
        }
    }

    private fun probe(): Boolean {
        testProbe?.let { return it() }
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("$HERMES_URL/api/status").openConnection() as HttpURLConnection).apply {
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
            HermesProbe.isHermesStatus(code, body)
        } catch (e: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    private fun showState(newState: ConnectionState) {
        state = newState
        val (icon, color, desc) = when (newState) {
            ConnectionState.CONNECTED -> Triple(R.drawable.ic_antenna, COLOR_CONNECTED, R.string.status_connected)
            ConnectionState.CHECKING -> Triple(R.drawable.ic_antenna, COLOR_CHECKING, R.string.status_checking)
            ConnectionState.DISCONNECTED -> Triple(R.drawable.ic_antenna_off, COLOR_DISCONNECTED, R.string.status_disconnected)
        }
        statusIcon.setImageResource(icon)
        statusIcon.imageTintList = ColorStateList.valueOf(color)
        statusIcon.contentDescription = getString(desc)
        statusIcon.tag = newState.name
        if (newState == ConnectionState.CHECKING) {
            if (pulse == null) {
                pulse = ObjectAnimator.ofFloat(statusIcon, View.ALPHA, 1f, 0.35f).apply {
                    duration = 700
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    start()
                }
            }
        } else {
            pulse?.cancel()
            pulse = null
            statusIcon.alpha = 1f
        }
    }

    private fun offlineHtml(): String = """
        <html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
        <body style="background:#170d02;color:#eee;font-family:sans-serif;padding:32px;line-height:1.7">
        <h2 style="color:#ffac02">Hermesに接続できません</h2>
        <ul><li>Tailscaleアプリがオンか確認してください</li>
        <li>PC側のHermes Web UIが起動しているか確認してください</li></ul>
        <p>接続が戻ると自動で表示します。右下の更新ボタンで今すぐ再試行できます。</p>
        </body></html>
    """.trimIndent()

    @VisibleForTesting internal fun setPanelOpen(open: Boolean) {
        shortcutPanel.visibility = if (open) View.VISIBLE else View.GONE
        menuButton.setBackgroundResource(if (open) R.drawable.bg_icon_button_active else R.drawable.bg_icon_button)
        menuButton.imageTintList = ColorStateList.valueOf(if (open) Color.parseColor("#FF170D02") else Color.WHITE)
        menuButton.contentDescription = getString(if (open) R.string.menu_close else R.string.menu_open)
    }

    private fun buildChips(container: android.view.ViewGroup, items: List<Shortcut>) {
        val density = resources.displayMetrics.density
        for (item in items) {
            val chip = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_chip)
                setPadding((14 * density).toInt(), (8 * density).toInt(), (14 * density).toInt(), (8 * density).toInt())
                minimumWidth = (72 * density).toInt()
                isClickable = true
                isFocusable = true
                contentDescription = "${item.label} ${item.description}"
                setOnClickListener { sendShortcut(item) }
            }
            chip.addView(TextView(this).apply {
                text = item.label
                setTextColor(Color.WHITE)
                textSize = 15f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            chip.addView(TextView(this).apply {
                text = item.description
                setTextColor(Color.parseColor("#FF9A9AA0"))
                textSize = 11f
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            container.addView(chip)
        }
    }

    private val terminalSendScript by lazy {
        assets.open("terminal-send.js").bufferedReader().use { it.readText() }
    }

    /** Send to the chat terminal; on other pages fall back to a native key (if any). */
    @VisibleForTesting internal fun sendShortcut(item: Shortcut) {
        keyDispatcher.cancel()
        val call = "(" + terminalSendScript + ")(" + org.json.JSONArray(item.chunks) + "," + item.gapMs + ")"
        webView.evaluateJavascript(call) { result ->
            if (result == "\"sent\"") return@evaluateJavascript
            val key = item.fallbackKey
            if (key == null) {
                Toast.makeText(this, R.string.terminal_only, Toast.LENGTH_SHORT).show()
                return@evaluateJavascript
            }
            val now = android.os.SystemClock.uptimeMillis()
            webView.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, key, 0, item.fallbackMeta))
            webView.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, key, 0, item.fallbackMeta))
        }
    }

    private val focusScript by lazy {
        assets.open("focus-input.js").bufferedReader().use { it.readText() }
    }

    private val keyDispatcher by lazy {
        KeyDispatcher(
            focus = { done ->
                webView.requestFocus()
                webView.evaluateJavascript(focusScript) { result -> done(result == "true") }
            },
            deliver = { key ->
                webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
                webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
            }
        )
    }

    private fun applyFullscreen() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
    }

    override fun onResume() {
        super.onResume()
        applyFullscreen()
        main.removeCallbacks(pollRunnable)
        main.post(pollRunnable)
    }

    override fun onPause() {
        main.removeCallbacks(pollRunnable)
        super.onPause()
    }

    override fun onDestroy() {
        destroyed = true
        main.removeCallbacksAndMessages(null)
        pulse?.cancel()
        try { connectivity?.unregisterNetworkCallback(networkCallback) } catch (e: RuntimeException) { }
        ownExecutor.shutdownNow()
        keyDispatcher.cancel()
        webView.stopLoading()
        (webView.parent as? android.view.ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
