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

    @Before fun setUp() {
        MainActivity.testExecutor = java.util.concurrent.Executor { it.run() }
        MainActivity.testProbe = { up }
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
        assertEquals(R.id.bottom_bar, params.bottomToTop)
        // Antenna is the first (left-most) child, refresh the last.
        val bar = a.findViewById<android.view.ViewGroup>(R.id.bottom_bar)
        assertEquals(R.id.iv_status, bar.getChildAt(0).id)
        assertEquals(R.id.btn_refresh, bar.getChildAt(bar.childCount - 1).id)
        c.pause().stop().destroy()
    }

    private fun resources(name: String, a: MainActivity) =
        a.resources.getIdentifier(name, "id", a.packageName)

    @Test fun alwaysLoadsTailscaleOrigin() {
        val c = launch()
        assertEquals(MainActivity.HERMES_URL, c.get().findViewById<WebView>(R.id.webview).url)
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

    @Test fun probeAcceptsOnlyHermesStatus() {
        assertTrue(HermesProbe.isHermesStatus(200, """{"version":"0.22.5","gateway_state":"running"}"""))
        assertFalse(HermesProbe.isHermesStatus(302, """{"version":"x","gateway_state":"y"}"""))
        assertFalse(HermesProbe.isHermesStatus(200, "<html>router</html>"))
    }
}
