package com.hermes.webui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Where the dashboard lives. Shown on first launch (nothing saved yet) and from the gear button.
 * Saving does not require a successful test: the server may simply be asleep right now.
 */
class SetupActivity : AppCompatActivity() {

    companion object {
        /** Set by MainActivity when the user opened settings from the running app. */
        const val EXTRA_FROM_APP = "from_app"

        @VisibleForTesting internal var testProbe: ((String) -> Boolean)? = null
        @VisibleForTesting internal var testExecutor: Executor? = null
    }

    private lateinit var input: EditText
    private lateinit var status: TextView
    private val main = Handler(Looper.getMainLooper())
    private val ownExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val executor: Executor get() = testExecutor ?: ownExecutor
    private var generation = 0
    private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        // From targetSdk 35 the window is always edge-to-edge: keep the form clear of the status bar,
        // the navigation bar / display cutout and the keyboard.
        val root = findViewById<android.view.View>(R.id.setup_root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val types = androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                androidx.core.view.WindowInsetsCompat.Type.displayCutout() or
                androidx.core.view.WindowInsetsCompat.Type.ime()
            val b = insets.getInsets(types)
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        input = findViewById(R.id.et_url)
        status = findViewById(R.id.tv_result)
        if (savedInstanceState == null) input.setText(ServerConfig.get(this) ?: "")
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { save(); true } else false
        }
        findViewById<Button>(R.id.btn_test).setOnClickListener { test() }
        findViewById<Button>(R.id.btn_save).setOnClickListener { save() }
    }

    private fun parsed(): ServerUrl.Result.Ok? = when (val r = ServerUrl.parse(input.text.toString())) {
        is ServerUrl.Result.Ok -> r
        is ServerUrl.Result.Error -> { showResult(errorText(r.reason), Color.parseColor("#FF453A")); null }
    }

    private fun errorText(reason: ServerUrl.Reason): String = getString(when (reason) {
        ServerUrl.Reason.EMPTY -> R.string.err_empty
        ServerUrl.Reason.BAD_SCHEME -> R.string.err_scheme
        ServerUrl.Reason.NO_HOST, ServerUrl.Reason.MALFORMED -> R.string.err_malformed
        ServerUrl.Reason.BAD_PORT -> R.string.err_port
        ServerUrl.Reason.CREDENTIALS -> R.string.err_credentials
        ServerUrl.Reason.QUERY -> R.string.err_query
    })

    @VisibleForTesting internal fun test() {
        val ok = parsed() ?: return
        input.setText(ok.url)
        input.setSelection(ok.url.length)
        val token = ++generation
        showResult(getString(R.string.test_running), Color.parseColor("#8E8E93"))
        val probe = testProbe ?: HermesProbe::check
        executor.execute {
            val up = probe(ok.url)
            main.post {
                if (destroyed || token != generation) return@post
                if (up) showResult(getString(R.string.test_ok), Color.parseColor("#34C759"))
                else showResult(getString(R.string.test_fail), Color.parseColor("#FF453A"))
            }
        }
    }

    @VisibleForTesting internal fun save() {
        val ok = parsed() ?: return
        if (ok.warning == ServerUrl.Warning.CLEARTEXT_PUBLIC) {
            AlertDialog.Builder(this)
                .setTitle(R.string.warn_title)
                .setMessage(R.string.warn_cleartext)
                .setNegativeButton(R.string.warn_back, null)
                .setPositiveButton(R.string.warn_continue) { _, _ -> commit(ok.url) }
                .show()
            return
        }
        commit(ok.url)
    }

    private fun commit(url: String) {
        ServerConfig.set(this, url)
        if (!intent.getBooleanExtra(EXTRA_FROM_APP, false)) {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }

    private fun showResult(text: String, color: Int) {
        status.text = text
        status.setTextColor(color)
    }

    override fun onDestroy() {
        destroyed = true
        main.removeCallbacksAndMessages(null)
        ownExecutor.shutdownNow()
        super.onDestroy()
    }
}
