package com.hermes.webui

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
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var btnAuto: MaterialButton
    private lateinit var btnLocal: MaterialButton
    private lateinit var btnVpn: MaterialButton
    private lateinit var btnRefresh: MaterialButton
    private lateinit var btnUp: MaterialButton
    private lateinit var btnDown: MaterialButton
    private lateinit var btnEnter: MaterialButton
    private lateinit var tvStatus: TextView

    companion object {
        const val LOCAL_URL = "http://192.168.1.27:8899"
        const val VPN_URL = "http://100.124.137.25:8899"
        const val PREF_MODE = "mode"
        private const val CONNECT_TIMEOUT_MS = 1500
        private const val READ_TIMEOUT_MS = 2000
        private const val NETWORK_DEBOUNCE_MS = 1000L
        private const val ERROR_RECHECK_MS = 3000L

        @VisibleForTesting internal var testProbe: ((String) -> Boolean)? = null
        @VisibleForTesting internal var testLocalNetwork: (() -> Boolean)? = null
        @VisibleForTesting internal var testExecutor: Executor? = null
    }

    private val main = Handler(Looper.getMainLooper())
    private val ownExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val executor: Executor get() = testExecutor ?: ownExecutor
    private var connectivity: ConnectivityManager? = null
    private var mode = Mode.AUTO
    private var currentRoute: Route? = null
    private var evalGeneration = 0
    private var lastEvalAt = 0L
    @Volatile private var networkSignature: String? = null
    private var pageFailed = false
    private var clearHistoryOnFinish = false
    private var destroyed = false

    @VisibleForTesting internal val activeRoute: Route? get() = currentRoute

    private val evaluateRunnable = Runnable { evaluate() }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = networkChanged(force = true)
        override fun onLost(network: Network) = networkChanged(force = true)
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
            networkChanged(force = false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applyFullscreen()

        webView = findViewById(R.id.webview)
        btnAuto = findViewById(R.id.btn_auto)
        btnLocal = findViewById(R.id.btn_local)
        btnVpn = findViewById(R.id.btn_vpn)
        btnRefresh = findViewById(R.id.btn_refresh)
        btnUp = findViewById(R.id.btn_up)
        btnDown = findViewById(R.id.btn_down)
        btnEnter = findViewById(R.id.btn_enter)
        tvStatus = findViewById(R.id.tv_status)

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        // Enable keyboard events for arrow key navigation
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                val target = RouteSelector.nousLoginRewrite(url, LOCAL_URL, VPN_URL) ?: return false
                // The dashboard's Nous OAuth callback is registered on the Tailscale address only;
                // starting from the LAN address ends in "Missing PKCE state cookie".
                if (!hasVpn()) {
                    android.widget.Toast.makeText(this@MainActivity,
                        "Nousでのログインには Tailscale が必要です。オンにするか、ユーザー名とパスワードでログインしてください",
                        android.widget.Toast.LENGTH_LONG).show()
                    return true
                }
                view?.loadUrl(target)
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (url != null && url.startsWith("http")) pageFailed = false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (url != null && url.startsWith(VPN_URL) && currentRoute == Route.LAN) {
                    // Nous sign-in moved this session to the Tailscale origin.
                    tvStatus.text = (if (mode == Mode.AUTO) "自動 " else "") + "● VPN接続 (Nousログイン)"
                    tvStatus.setTextColor(Color.parseColor("#29B6F6"))
                }
                if (clearHistoryOnFinish && url != null && url.startsWith("http")) {
                    clearHistoryOnFinish = false
                    view?.clearHistory()
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame != true) return
                pageFailed = true
                // The current route stopped answering: re-check soon (throttled).
                if (mode == Mode.AUTO && SystemClock.elapsedRealtime() - lastEvalAt > ERROR_RECHECK_MS) {
                    scheduleEvaluate(500)
                }
            }
        }

        val preferences = getSharedPreferences("connection", MODE_PRIVATE)
        mode = Mode.parse(preferences.getString(PREF_MODE, null))
        styleModeButtons()

        btnAuto.setOnClickListener { selectMode(Mode.AUTO) }
        btnLocal.setOnClickListener { selectMode(Mode.LAN) }
        btnVpn.setOnClickListener { selectMode(Mode.VPN) }
        btnRefresh.setOnClickListener {
            keyDispatcher.cancel()
            if (currentRoute == null || currentRoute == Route.NONE || pageFailed) {
                currentRoute = null
                evaluate()
            } else {
                webView.reload()
            }
        }
        btnUp.setOnClickListener { keyDispatcher.send(KeyEvent.KEYCODE_DPAD_UP) }
        btnDown.setOnClickListener { keyDispatcher.send(KeyEvent.KEYCODE_DPAD_DOWN) }
        btnEnter.setOnClickListener { keyDispatcher.send(KeyEvent.KEYCODE_ENTER) }

        connectivity = getSystemService(ConnectivityManager::class.java)
        try {
            val request = NetworkRequest.Builder()
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            connectivity?.registerNetworkCallback(request, networkCallback)
        } catch (e: RuntimeException) {
            // Monitoring is best effort; onResume and load errors still trigger re-checks.
        }
    }

    private fun selectMode(newMode: Mode) {
        mode = newMode
        getSharedPreferences("connection", MODE_PRIVATE).edit().putString(PREF_MODE, newMode.name).apply()
        styleModeButtons()
        currentRoute = null
        evaluate()
    }

    /** Called on the connectivity thread. Ignore signal-strength-only capability changes. */
    private fun networkChanged(force: Boolean) {
        val signature = "${hasLocalNetwork()}/${hasVpn()}"
        if (!force && signature == networkSignature) return
        networkSignature = signature
        scheduleEvaluate(NETWORK_DEBOUNCE_MS)
    }

    private fun scheduleEvaluate(delayMs: Long) {
        main.removeCallbacks(evaluateRunnable)
        main.postDelayed(evaluateRunnable, delayMs)
    }

    /** Probe off the UI thread; apply the result only if no newer evaluation started. */
    private fun evaluate() {
        if (destroyed) return
        main.removeCallbacks(evaluateRunnable)
        val token = ++evalGeneration
        lastEvalAt = SystemClock.elapsedRealtime()
        val m = mode
        val local = hasLocalNetwork()

        // Manual modes load immediately; probing only updates the status label.
        if (m != Mode.AUTO) switchTo(if (m == Mode.LAN) Route.LAN else Route.VPN)
        showStatus(null, reachable = false, checking = true)

        executor.execute {
            var lanOk: Boolean? = null
            var vpnOk: Boolean? = null
            val route = RouteSelector.select(m, local,
                { probe(LOCAL_URL).also { lanOk = it } },
                { probe(VPN_URL).also { vpnOk = it } })
            val reachable = when (route) {
                Route.LAN -> lanOk ?: probe(LOCAL_URL)
                Route.VPN -> vpnOk ?: probe(VPN_URL)
                Route.NONE -> false
            }
            main.post {
                if (destroyed || token != evalGeneration || m != mode) return@post
                val changed = route != currentRoute
                switchTo(route)
                if (!changed && reachable && pageFailed && route != Route.NONE) webView.loadUrl(urlFor(route))
                showStatus(route, reachable, checking = false)
            }
        }
    }

    private fun switchTo(route: Route) {
        if (route == currentRoute) return
        currentRoute = route
        keyDispatcher.cancel()
        if (route == Route.NONE) {
            webView.loadDataWithBaseURL(null, offlineHtml(), "text/html", "utf-8", null)
        } else {
            clearHistoryOnFinish = true
            webView.loadUrl(urlFor(route))
        }
    }

    private fun urlFor(route: Route) = if (route == Route.VPN) VPN_URL else LOCAL_URL

    /** Hermes answers GET /api/status without login; any other host on 192.168.1.27 fails the check. */
    private fun probe(base: String): Boolean {
        testProbe?.let { return it(base) }
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("$base/api/status").openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                useCaches = false
            }
            val code = conn.responseCode
            val body = if (code == 200) {
                conn.inputStream.use { input ->
                    val buf = ByteArray(8192)
                    var total = 0
                    while (total < buf.size) {
                        val n = input.read(buf, total, buf.size - total)
                        if (n < 0) break
                        total += n
                    }
                    String(buf, 0, total, Charsets.UTF_8)
                }
            } else ""
            RouteSelector.isHermesStatus(code, body)
        } catch (e: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    private fun capsOfAll(): List<NetworkCapabilities> {
        val cm = connectivity ?: return emptyList()
        return try {
            @Suppress("DEPRECATION")
            cm.allNetworks.mapNotNull { cm.getNetworkCapabilities(it) }
        } catch (e: RuntimeException) {
            emptyList()
        }
    }

    private fun hasLocalNetwork(): Boolean {
        testLocalNetwork?.let { return it() }
        return capsOfAll().any {
            !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                (it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        }
    }

    private fun hasVpn(): Boolean = capsOfAll().any { it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) }

    private fun styleModeButtons() {
        val on = ColorStateList.valueOf(Color.parseColor("#1E88E5"))
        val off = ColorStateList.valueOf(Color.parseColor("#424242"))
        listOf(btnAuto to Mode.AUTO, btnLocal to Mode.LAN, btnVpn to Mode.VPN).forEach { (b, m) ->
            val selected = m == mode
            b.isSelected = selected
            b.backgroundTintList = if (selected) on else off
            b.setTextColor(if (selected) Color.WHITE else Color.parseColor("#BDBDBD"))
        }
    }

    private fun showStatus(route: Route?, reachable: Boolean, checking: Boolean) {
        val prefix = if (mode == Mode.AUTO) "自動 " else ""
        val (text, color) = when {
            checking -> "● 確認中…" to "#BDBDBD"
            route == Route.LAN && reachable -> "● LAN接続" to "#66BB6A"
            route == Route.VPN && reachable -> "● VPN接続 (Tailscale)" to "#29B6F6"
            route == Route.LAN -> "● LAN 応答なし" to "#EF5350"
            route == Route.VPN -> "● VPN 応答なし" to "#EF5350"
            else -> "● 接続できません" to "#EF5350"
        }
        tvStatus.text = prefix + text
        tvStatus.setTextColor(Color.parseColor(color))
    }

    private fun offlineHtml(): String = """
        <html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
        <body style="background:#111;color:#eee;font-family:sans-serif;padding:32px;line-height:1.7">
        <h2>Hermesに接続できません</h2>
        <p>自宅のWi-Fi（LAN）とTailscale（VPN）のどちらからも応答がありません。</p>
        <ul><li>外出先ではTailscaleアプリをオンにしてください</li>
        <li>自宅ではWi-Fiに接続してください</li>
        <li>PC側のHermes Web UIが起動しているか確認してください</li></ul>
        <p>ネットワークが変わると自動で再接続します。↻で今すぐ再確認できます。</p>
        </body></html>
    """.trimIndent()

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
        // Network may have changed while in background (e.g. left home).
        if (mode == Mode.AUTO || currentRoute == null) scheduleEvaluate(0)
    }

    override fun onDestroy() {
        destroyed = true
        main.removeCallbacksAndMessages(null)
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
