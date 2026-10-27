package com.hermes.webui

import android.content.Context
import android.widget.Button
import android.webkit.WebView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MainActivityTest {
    @Before fun clearPreferences() {
        RuntimeEnvironment.getApplication().getSharedPreferences("connection", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun controlsDoNotCoverWebContent() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val root = activity.findViewById<WebView>(R.id.webview).parent as android.view.View
        root.measure(android.view.View.MeasureSpec.makeMeasureSpec(800, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(1200, android.view.View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 800, 1200)
        val web = activity.findViewById<WebView>(R.id.webview)
        val top = activity.findViewById<android.view.View>(R.id.url_switcher)
        val bottom = activity.findViewById<android.view.View>(R.id.arrow_controls)
        // Robolectric's WebView shadow does not perform Chromium layout.
        // Assert the actual ConstraintLayout anchors rather than shadow bounds.
        val params = web.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        assertEquals(top.id, params.topToBottom)
        assertEquals(bottom.id, params.bottomToTop)
        assertTrue(top.bottom < bottom.top)
        controller.pause().stop().destroy()
    }

    @Test fun remembersVpnAfterRestart() {
        val first = Robolectric.buildActivity(MainActivity::class.java).setup()
        first.get().findViewById<Button>(R.id.btn_vpn).performClick()
        first.pause().stop().destroy()
        val second = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertEquals(MainActivity.VPN_URL, second.get().findViewById<WebView>(R.id.webview).url)
        assertTrue(second.get().findViewById<Button>(R.id.btn_vpn).isSelected)
        second.pause().stop().destroy()
    }
}
