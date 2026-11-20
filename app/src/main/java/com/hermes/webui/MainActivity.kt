package com.hermes.webui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
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
import androidx.activity.OnBackPressedCallback
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Full-screen WebView for a self-hosted Hermes dashboard at the one address saved in [SetupActivity].
 * The dashboard's OAuth callback and login cookies are bound to one origin,
 * so there is deliberately no automatic LAN/VPN switching.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val POLL_MS = 15_000L
        private const val NETWORK_DEBOUNCE_MS = 1_000L
        /** A server load still pending this long while /api/status fails is treated as down. */
        @VisibleForTesting internal const val HANG_MS = 10_000L
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

    /** Dashboard base URL currently loaded (no trailing slash). */
    @VisibleForTesting internal var serverUrl = ""
        private set
    private var ready = false
    private var clearHistoryOnLoad = false
    private lateinit var tokenKeeper: PtyTokenKeeper
    /**
     * A main-frame load we asked for has not finished yet. Tracked from our own loadUrl/reload
     * calls because the WebView reports onPageStarted only once a response arrives, so a
     * connection that hangs never "starts".
     */
    @VisibleForTesting internal var pageLoading = false

    private var loadStartedAt = 0L

    private fun loadServer() {
        pageLoading = true
        loadStartedAt = android.os.SystemClock.uptimeMillis()
        webView.loadUrl(serverUrl)
    }

    /**
     * Back goes through the page history first. From Android 16 (targetSdk 36) onBackPressed() is
     * no longer called, so this must be an OnBackPressedCallback; it is enabled only while the page
     * can go back, which also lets the system play the predictive "back to home" animation.
     */
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (webView.canGoBack()) webView.goBack()
            updateBack()
        }
    }

    /** The Chat page is open and the server is reachable: keep the display awake. */
    @VisibleForTesting internal var keepScreenOn = false
        private set

    private fun updateKeepScreenOn() {
        if (!ready) return
        val on = state == ConnectionState.CONNECTED && !offlineShown && ChatPage.isChat(serverUrl, webView.url)
        if (on == keepScreenOn) return
        keepScreenOn = on
        // Window flag, not a wake lock: it only applies while this screen is visible and needs no permission.
        if (on) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun updateBack() {
        updateKeepScreenOn()
        backCallback.isEnabled = ready && webView.canGoBack()
    }

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
        val saved = ServerConfig.get(this)
        if (saved == null) {
            // First run: ask for the address before showing anything.
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        serverUrl = saved
        ready = true
        setContentView(R.layout.activity_main)
        applyFullscreen()
        installInsets()

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

            override fun onPageFinished(view: WebView?, url: String?) {
                pageLoading = false
                if (clearHistoryOnLoad && url != null && url.startsWith(serverUrl)) {
                    // A new server must not be reachable through Back from the old one.
                    clearHistoryOnLoad = false
                    view?.clearHistory()
                }
                updateBack()
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) = updateBack()

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
            if (offlineShown || pageFailed || webView.url.isNullOrEmpty()) loadServer()
            else { pageLoading = true; loadStartedAt = android.os.SystemClock.uptimeMillis(); webView.reload() }
            checkConnection()
        }
        findViewById<View>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java).putExtra(SetupActivity.EXTRA_FROM_APP, true))
        }

        onBackPressedDispatcher.addCallback(this, backCallback)

        // Re-attach the chat terminal after the app was killed instead of opening a new, empty session.
        tokenKeeper = PtyTokenKeeper(this, webView)
        tokenKeeper.install(serverUrl)

        showState(ConnectionState.CHECKING)
        loadServer()

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
        val url = serverUrl
        executor.execute {
            val ok = probe(url)
            main.post {
                if (destroyed || token != generation) return@post
                onProbeResult(ok)
            }
        }
    }

    private fun onProbeResult(ok: Boolean) {
        showState(if (ok) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED)
        if (ok && (pageFailed || offlineShown)) {
            // Connection came back: leave the error page automatically, and keep the help page
            // out of the Back history.
            clearHistoryOnLoad = true
            loadServer()
        } else if (!ok && !offlineShown && (pageFailed || pageLoading)) {
            val pending = android.os.SystemClock.uptimeMillis() - loadStartedAt
            if (pageFailed || pending >= HANG_MS) {
                // A blocked port over a VPN often never refuses: the WebView would wait a minute or
                // more for its own timeout. Replace the hanging navigation with the help page.
                // (No stopLoading(): on a real WebView it also cancels this load.)
                pageLoading = false
                offlineShown = true
                webView.loadDataWithBaseURL(null, offlineHtml(), "text/html", "utf-8", null)
            } else {
                // Maybe just a slow server: look again once the load has hung long enough.
                scheduleCheck(HANG_MS - pending)
            }
        }
    }

    private fun probe(url: String): Boolean = testProbe?.invoke() ?: HermesProbe.check(url)

    private fun showState(newState: ConnectionState) {
        state = newState
        updateKeepScreenOn()
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

    private fun offlineHtml(): String {
        fun t(id: Int) = android.text.TextUtils.htmlEncode(getString(id))
        return """
        <html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
        <body style="background:#170d02;color:#eee;font-family:sans-serif;padding:32px;line-height:1.7">
        <h2 style="color:#ffac02">${t(R.string.offline_title)}</h2>
        <ul><li>${t(R.string.offline_vpn)}</li>
        <li>${t(R.string.offline_server)}</li>
        <li>${t(R.string.offline_address)}</li></ul>
        <p>${t(R.string.offline_retry)}</p>
        </body></html>
        """.trimIndent()
    }

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
                contentDescription = "${item.label} ${getString(item.description)}"
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
                text = getString(item.description)
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

    /**
     * Immersive full screen WITHOUT the FULLSCREEN window flag: that flag makes
     * adjustResize a no-op, so the soft keyboard used to cover the input line.
     * The keyboard (IME) inset is applied as bottom padding on the root instead,
     * which shrinks the WebView exactly like a resize would.
     */
    private fun applyFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun installInsets() {
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    override fun onResume() {
        super.onResume()
        if (!ready) return
        applyFullscreen()
        val current = ServerConfig.get(this)
        if (current != null && current != serverUrl) {
            // Address changed in settings: open the new server and drop the old history.
            serverUrl = current
            keyDispatcher.cancel()
            clearHistoryOnLoad = true
            tokenKeeper.install(serverUrl)
            loadServer()
            showState(ConnectionState.CHECKING)
            checkConnection()
        }
        main.removeCallbacks(pollRunnable)
        main.post(pollRunnable)
    }

    override fun onPause() {
        if (!ready) return super.onPause()
        main.removeCallbacks(pollRunnable)
        super.onPause()
    }

    override fun onDestroy() {
        destroyed = true
        if (!ready) {
            ownExecutor.shutdownNow()
            return super.onDestroy()
        }
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

}
