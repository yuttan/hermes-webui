package com.hermes.webui

import android.content.Context
import android.os.Looper
import android.webkit.WebView
import android.widget.Button
import android.widget.TextView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MainActivityTest {
    private var lanUp = true
    private var vpnUp = true
    private var onWifi = true

    @Before fun setUp() {
        RuntimeEnvironment.getApplication().getSharedPreferences("connection", Context.MODE_PRIVATE).edit().clear().commit()
        MainActivity.testExecutor = java.util.concurrent.Executor { it.run() }
        MainActivity.testLocalNetwork = { onWifi }
        MainActivity.testProbe = { url -> if (url == MainActivity.LOCAL_URL) lanUp else vpnUp }
    }

    @After fun tearDown() {
        MainActivity.testExecutor = null
        MainActivity.testLocalNetwork = null
        MainActivity.testProbe = null
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun launch() = Robolectric.buildActivity(MainActivity::class.java).setup().also { idle() }

    private fun status(a: MainActivity) = a.findViewById<TextView>(R.id.tv_status).text.toString()

    @Test fun controlsDoNotCoverWebContent() {
        val controller = launch()
        val activity = controller.get()
        val root = activity.findViewById<WebView>(R.id.webview).parent as android.view.View
        root.measure(android.view.View.MeasureSpec.makeMeasureSpec(800, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(1200, android.view.View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 800, 1200)
        val web = activity.findViewById<WebView>(R.id.webview)
        val top = activity.findViewById<android.view.View>(R.id.url_switcher)
        val bottom = activity.findViewById<android.view.View>(R.id.arrow_controls)
        val params = web.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        assertEquals(top.id, params.topToBottom)
        assertEquals(bottom.id, params.bottomToTop)
        assertTrue(top.bottom < bottom.top)
        controller.pause().stop().destroy()
    }

    @Test fun autoIsDefaultAndUsesLanOnHomeWifi() {
        val c = launch()
        val a = c.get()
        assertTrue(a.findViewById<Button>(R.id.btn_auto).isSelected)
        assertEquals(Route.LAN, a.activeRoute)
        assertEquals(MainActivity.LOCAL_URL, a.findViewById<WebView>(R.id.webview).url)
        assertEquals("自動 ● LAN接続", status(a))
        c.pause().stop().destroy()
    }

    @Test fun autoUsesVpnOutsideHome() {
        onWifi = false
        val c = launch()
        assertEquals(Route.VPN, c.get().activeRoute)
        assertEquals(MainActivity.VPN_URL, c.get().findViewById<WebView>(R.id.webview).url)
        assertEquals("自動 ● VPN接続 (Tailscale)", status(c.get()))
        c.pause().stop().destroy()
    }

    @Test fun autoUsesVpnOnForeignWifi() {
        lanUp = false
        val c = launch()
        assertEquals(Route.VPN, c.get().activeRoute)
        c.pause().stop().destroy()
    }

    @Test fun autoShowsOfflineWhenNothingAnswers() {
        lanUp = false; vpnUp = false
        val c = launch()
        assertEquals(Route.NONE, c.get().activeRoute)
        assertEquals("自動 ● 接続できません", status(c.get()))
        c.pause().stop().destroy()
    }

    @Test fun autoSwitchesWhenLeavingHomeOnResume() {
        val c = launch()
        assertEquals(Route.LAN, c.get().activeRoute)
        c.pause()
        onWifi = false
        c.resume(); idle()
        assertEquals(Route.VPN, c.get().activeRoute)
        c.pause().stop().destroy()
    }

    @Test fun manualVpnIsRememberedAfterRestart() {
        val first = launch()
        first.get().findViewById<Button>(R.id.btn_vpn).performClick()
        idle()
        first.pause().stop().destroy()
        val second = launch()
        assertEquals(MainActivity.VPN_URL, second.get().findViewById<WebView>(R.id.webview).url)
        assertTrue(second.get().findViewById<Button>(R.id.btn_vpn).isSelected)
        assertFalse(second.get().findViewById<Button>(R.id.btn_auto).isSelected)
        assertEquals("● VPN接続 (Tailscale)", status(second.get()))
        second.pause().stop().destroy()
    }

    @Test fun manualLanReportsNoResponse() {
        lanUp = false
        val c = launch()
        c.get().findViewById<Button>(R.id.btn_local).performClick()
        idle()
        assertEquals(Route.LAN, c.get().activeRoute)
        assertEquals("● LAN 応答なし", status(c.get()))
        c.pause().stop().destroy()
    }
}
