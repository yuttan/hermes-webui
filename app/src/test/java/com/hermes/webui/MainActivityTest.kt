package com.hermes.webui

import android.os.Looper
import android.view.View
import android.webkit.WebView
import android.widget.ImageView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MainActivityTest {
    private var up = true

    private val url = "http://100.64.0.1:9119"
    private val ctx get() = org.robolectric.RuntimeEnvironment.getApplication()

    @Before fun setUp() {
        MainActivity.testExecutor = java.util.concurrent.Executor { it.run() }
        MainActivity.testProbe = { up }
        ServerConfig.set(ctx, url)
    }

    @After fun tearDown() {
        MainActivity.testExecutor = null
        MainActivity.testProbe = null
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun launch() = Robolectric.buildActivity(MainActivity::class.java).setup().also { idle() }
    private fun icon(a: MainActivity) = a.findViewById<ImageView>(R.id.iv_status)

    @Test fun noTopBarAndWebViewSitsAboveBottomBar() {
        val c = launch()
        val a = c.get()
        assertNull(a.findViewById<View?>(resources("url_switcher", a)))
        val web = a.findViewById<WebView>(R.id.webview)
        val params = web.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        assertEquals(androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID, params.topToTop)
        assertEquals(R.id.shortcut_panel, params.bottomToTop)
        // Antenna is the first (left-most) child, refresh the last.
        val bar = a.findViewById<android.view.ViewGroup>(R.id.bottom_bar)
        assertEquals(R.id.iv_status, bar.getChildAt(0).id)
        assertEquals(R.id.btn_refresh, bar.getChildAt(bar.childCount - 1).id)
        c.pause().stop().destroy()
    }

    private fun resources(name: String, a: MainActivity) =
        a.resources.getIdentifier(name, "id", a.packageName)

    @Test fun loadsSavedServer() {
        val c = launch()
        assertEquals(url, c.get().serverUrl)
        assertEquals(url, c.get().findViewById<WebView>(R.id.webview).url)
        c.pause().stop().destroy()
    }

    @Test fun firstRunOpensSetupInsteadOfWebView() {
        ctx.getSharedPreferences("server", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val c = Robolectric.buildActivity(MainActivity::class.java).setup().also { idle() }
        val a = c.get()
        assertTrue(a.isFinishing)
        assertEquals(SetupActivity::class.java.name, shadowOf(a).nextStartedActivity.component?.className)
        c.pause().stop().destroy()
    }

    @Test fun gearOpensSettingsAndNewAddressIsLoadedOnReturn() {
        val c = launch()
        val a = c.get()
        a.findViewById<View>(R.id.btn_settings).performClick()
        val started = shadowOf(a).nextStartedActivity
        assertEquals(SetupActivity::class.java.name, started.component?.className)
        assertTrue(started.getBooleanExtra(SetupActivity.EXTRA_FROM_APP, false))
        c.pause()
        ServerConfig.set(ctx, "https://pc.tail1.ts.net")
        c.resume()
        idle()
        assertEquals("https://pc.tail1.ts.net", a.serverUrl)
        assertEquals("https://pc.tail1.ts.net", a.findViewById<WebView>(R.id.webview).url)
        c.pause().stop().destroy()
    }

    @Test fun antennaShowsConnected() {
        val c = launch()
        assertEquals(ConnectionState.CONNECTED, c.get().state)
        assertEquals("Hermesに接続中", icon(c.get()).contentDescription)
        c.pause().stop().destroy()
    }

    @Test fun antennaShowsDisconnectedAndRecovers() {
        up = false
        val c = launch()
        assertEquals(ConnectionState.DISCONNECTED, c.get().state)
        assertEquals("Hermesに接続できません", icon(c.get()).contentDescription)
        up = true
        c.get().findViewById<View>(R.id.btn_refresh).performClick()
        idle()
        assertEquals(ConnectionState.CONNECTED, c.get().state)
        c.pause().stop().destroy()
    }

    @Test fun menuTogglesShortcutPanel() {
        val c = launch()
        val a = c.get()
        val panel = a.findViewById<View>(R.id.shortcut_panel)
        assertEquals(View.GONE, panel.visibility)
        a.findViewById<View>(R.id.btn_menu).performClick()
        assertEquals(View.VISIBLE, panel.visibility)
        assertEquals(Shortcuts.keys.size, a.findViewById<android.view.ViewGroup>(R.id.flow_keys).childCount)
        assertEquals(Shortcuts.commands.size, a.findViewById<android.view.ViewGroup>(R.id.flow_commands).childCount)
        a.findViewById<View>(R.id.btn_menu).performClick()
        assertEquals(View.GONE, panel.visibility)
        c.pause().stop().destroy()
    }

    @Test fun shortcutBytesMatchTuiBindings() {
        fun bytes(label: String) = Shortcuts.keys.first { it.label == label }.chunks
        assertEquals(listOf("\u001b"), bytes("Esc"))
        assertEquals(listOf("\u001b", "\u001b"), bytes("Esc×2"))
        assertEquals(listOf("\u0003"), bytes("Ctrl+C"))
        assertEquals(listOf("\u000f"), bytes("Ctrl+O"))
        assertEquals(listOf("\u0018"), bytes("Ctrl+X"))
        assertEquals(listOf("\u0015"), bytes("Ctrl+U"))
        assertEquals(listOf("\u001b[Z"), bytes("Shift+Tab"))
        assertEquals(listOf("\u001b\r"), bytes("Alt+Enter"))
        // Commands are inserted, never auto-submitted.
        assertTrue(Shortcuts.commands.all { s -> s.chunks.single().startsWith("/") && !s.chunks.single().contains('\r') })
    }

    @Test fun probeAcceptsOnlyHermesStatus() {
        assertTrue(HermesProbe.isHermesStatus(200, """{"version":"0.22.5","gateway_state":"running"}"""))
        assertFalse(HermesProbe.isHermesStatus(302, """{"version":"x","gateway_state":"y"}"""))
        assertFalse(HermesProbe.isHermesStatus(200, "<html>router</html>"))
    }
}
