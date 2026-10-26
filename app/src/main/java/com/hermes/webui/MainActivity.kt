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

        // Load default URL (LAN)
        loadUrl(LOCAL_URL)
        btnLocal.isSelected = true

        // Button listeners
        btnLocal.setOnClickListener {
            loadUrl(LOCAL_URL)
            btnLocal.isSelected = true
            btnVpn.isSelected = false
        }

        btnVpn.setOnClickListener {
            loadUrl(VPN_URL)
            btnVpn.isSelected = true
            btnLocal.isSelected = false
        }

        btnRefresh.setOnClickListener {
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

    private fun sendKeyEvent(keyCode: Int) {
        webView.requestFocus()
        
        val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        
        // First, ensure focus is on the input area by executing JavaScript
        val jsKeyCode = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> 38
            KeyEvent.KEYCODE_DPAD_DOWN -> 40
            KeyEvent.KEYCODE_ENTER -> 13
            else -> keyCode
        }
        
        webView.post {
            // Focus the chat input area before sending key event
            webView.evaluateJavascript(
                "(function() { var inputs = document.querySelectorAll('input, textarea, [contenteditable=\"true\"]'); for (var i = 0; i < inputs.length; i++) { if (inputs[i].offsetParent !== null) { inputs[i].focus(); break; } } })()",
                null
            )
            
            // Small delay to ensure focus is set
            webView.postDelayed({
                webView.dispatchKeyEvent(downEvent)
                webView.dispatchKeyEvent(upEvent)
            }, 100)
        }
    }

    private fun loadUrl(url: String) {
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
