package com.hermes.webui

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SetupActivityTest {
    private val ctx get() = RuntimeEnvironment.getApplication()
    private val probed = mutableListOf<String>()
    private var up = true

    @Before fun setUp() {
        ctx.getSharedPreferences("server", Context.MODE_PRIVATE).edit().clear().commit()
        SetupActivity.testExecutor = java.util.concurrent.Executor { it.run() }
        SetupActivity.testProbe = { probed += it; up }
    }

    @After fun tearDown() {
        SetupActivity.testExecutor = null
        SetupActivity.testProbe = null
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun launch(fromApp: Boolean = false) = Robolectric.buildActivity(
        SetupActivity::class.java,
        Intent(ctx, SetupActivity::class.java).putExtra(SetupActivity.EXTRA_FROM_APP, fromApp)
    ).setup().also { idle() }

    private fun SetupActivity.type(text: String) = findViewById<EditText>(R.id.et_url).setText(text)
    private fun SetupActivity.result() = findViewById<TextView>(R.id.tv_result).text.toString()

    @Test fun firstRunSaveStoresNormalizedUrlAndOpensMain() {
        val a = launch().get()
        a.type("  mypc.tail1234.ts.net/  ")
        a.save()
        idle()
        assertEquals("https://mypc.tail1234.ts.net", ServerConfig.get(ctx))
        assertEquals(MainActivity::class.java.name, shadowOf(a).nextStartedActivity.component?.className)
        assertTrue(a.isFinishing)
    }

    @Test fun fromAppSaveJustReturns() {
        val a = launch(fromApp = true).get()
        a.type("http://100.80.1.2:9119")
        a.save()
        assertEquals("http://100.80.1.2:9119", ServerConfig.get(ctx))
        assertNull(shadowOf(a).nextStartedActivity)
        assertTrue(a.isFinishing)
    }

    @Test fun invalidAddressIsRejectedAndNotSaved() {
        val a = launch().get()
        a.type("ftp://example.com")
        a.save()
        assertNull(ServerConfig.get(ctx))
        assertFalse(a.isFinishing)
        assertEquals(a.getString(R.string.err_scheme), a.result())
    }

    @Test fun cleartextToPublicHostNeedsConfirmation() {
        val a = launch().get()
        a.type("http://hermes.example.com")
        a.save()
        idle()
        assertNull(ServerConfig.get(ctx))
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle()
        assertEquals("http://hermes.example.com", ServerConfig.get(ctx))
    }

    @Test fun connectionTestProbesNormalizedUrl() {
        val a = launch().get()
        a.type("100.80.1.2:9119")
        a.test()
        idle()
        assertEquals(listOf("http://100.80.1.2:9119"), probed)
        assertEquals(a.getString(R.string.test_ok), a.result())
        up = false
        a.test()
        idle()
        assertEquals(a.getString(R.string.test_fail), a.result())
    }

    @Test fun englishByDefault() {
        val a = launch().get()
        assertEquals("Test connection", a.getString(R.string.setup_test))
        assertEquals("Close / cancel", a.getString(Shortcuts.keys.first().description))
    }

    @Test @Config(qualifiers = "ja") fun japaneseOnJapaneseDevices() {
        val a = launch().get()
        assertEquals("接続テスト", a.getString(R.string.setup_test))
        assertEquals("閉じる・取消", a.getString(Shortcuts.keys.first().description))
        assertEquals("Hermes Remote", a.getString(R.string.app_name))
    }

    @Test @Config(qualifiers = "de") fun germanOnGermanDevices() {
        assertEquals("Verbindung testen", launch().get().getString(R.string.setup_test))
    }

    @Test @Config(qualifiers = "zh-rTW") fun traditionalChineseOnTaiwanDevices() {
        assertEquals("測試連線", launch().get().getString(R.string.setup_test))
    }

    @Test @Config(qualifiers = "zh-rHK") fun hongKongGetsTraditionalChinese() {
        assertEquals("測試連線", launch().get().getString(R.string.setup_test))
    }

    @Test @Config(qualifiers = "zh-rCN") fun mainlandGetsSimplifiedChinese() {
        assertEquals("测试连接", launch().get().getString(R.string.setup_test))
    }

    @Test fun prefillsSavedAddress() {
        ServerConfig.set(ctx, "https://pc.tail1.ts.net")
        val a = launch(fromApp = true).get()
        assertEquals("https://pc.tail1.ts.net", a.findViewById<EditText>(R.id.et_url).text.toString())
    }
}
