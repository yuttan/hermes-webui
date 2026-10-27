package com.hermes.webui

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var btnLocal: MaterialButton
    private lateinit var btnVpn: MaterialButton
    private lateinit var btnRefresh: MaterialButton
    private lateinit var btnUp: MaterialButton
    private lateinit var btnDown: MaterialButton
    private lateinit var btnEnter: MaterialButton

    companion object {
        const val LOCAL_URL = "http://192.168.1.27:8899"
        const val VPN_URL = "http://100.124.137.25:8899"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Fullscreen setup
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        // Initialize views
        webView = findViewById(R.id.webview)
        btnLocal = findViewById(R.id.btn_local)
        btnVpn = findViewById(R.id.btn_vpn)
        btnRefresh = findViewById(R.id.btn_refresh)
        btnUp = findViewById(R.id.btn_up)
        btnDown = findViewById(R.id.btn_down)
        btnEnter = findViewById(R.id.btn_enter)

        // Configure WebView
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
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return false
            }
        }

        // Remember the explicit connection choice, without automatic failover.
        val preferences = getSharedPreferences("connection", MODE_PRIVATE)
        val useVpn = preferences.getBoolean("vpn", false)
        loadUrl(if (useVpn) VPN_URL else LOCAL_URL)
        btnLocal.isSelected = !useVpn
        btnVpn.isSelected = useVpn

        // Button listeners
        btnLocal.setOnClickListener {
            preferences.edit().putBoolean("vpn", false).apply()
            loadUrl(LOCAL_URL)
            btnLocal.isSelected = true
            btnVpn.isSelected = false
        }

        btnVpn.setOnClickListener {
            preferences.edit().putBoolean("vpn", true).apply()
            loadUrl(VPN_URL)
            btnVpn.isSelected = true
            btnLocal.isSelected = false
        }

        btnRefresh.setOnClickListener {
            keyDispatcher.cancel()
            webView.reload()
        }

        // Arrow key navigation
        btnUp.setOnClickListener {
            sendKeyEvent(KeyEvent.KEYCODE_DPAD_UP)
        }

        btnDown.setOnClickListener {
            sendKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN)
        }

        btnEnter.setOnClickListener {
            sendKeyEvent(KeyEvent.KEYCODE_ENTER)
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

    private fun sendKeyEvent(keyCode: Int) {
        keyDispatcher.send(keyCode)
    }

    override fun onDestroy() {
        keyDispatcher.cancel()
        webView.stopLoading()
        (webView.parent as? android.view.ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    private fun loadUrl(url: String) {
        keyDispatcher.cancel()
        webView.loadUrl(url)
    }

    override fun onResume() {
        super.onResume()
        // Re-apply fullscreen on resume
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
