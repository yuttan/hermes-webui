package com.hermes.webui

import android.view.KeyEvent
import androidx.annotation.StringRes

/**
 * One entry in the collapsible shortcut panel.
 * [chunks] are raw bytes for the Hermes TUI (sent one chunk at a time, [gapMs] apart).
 * [fallbackKey]/[fallbackMeta] is a native key used on pages without the chat terminal.
 */
data class Shortcut(
    val label: String,
    @StringRes val description: Int,
    val chunks: List<String>,
    val fallbackKey: Int? = null,
    val fallbackMeta: Int = 0,
    val gapMs: Long = 150,
)

object Shortcuts {
    private const val ESC = "\u001b"
    private const val CTRL = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
    private const val SHIFT = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
    private const val ALT = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON

    private fun ctrl(letter: Char): String = (letter.uppercaseChar().code - 64).toChar().toString()

    /** Keys a tablet soft keyboard cannot type, with what they do in the Hermes TUI. */
    val keys: List<Shortcut> = listOf(
        Shortcut("Esc", R.string.sc_esc, listOf(ESC), KeyEvent.KEYCODE_ESCAPE),
        Shortcut("Esc×2", R.string.sc_esc2, listOf(ESC, ESC), gapMs = 150),
        Shortcut("Ctrl+C", R.string.sc_ctrl_c, listOf(ctrl('c')), KeyEvent.KEYCODE_C, CTRL),
        Shortcut("Ctrl+X", R.string.sc_ctrl_x, listOf(ctrl('x')), KeyEvent.KEYCODE_X, CTRL),
        Shortcut("Tab", R.string.sc_tab, listOf("\t"), KeyEvent.KEYCODE_TAB),
        Shortcut("Shift+Tab", R.string.sc_shift_tab, listOf("$ESC[Z"), KeyEvent.KEYCODE_TAB, SHIFT),
        Shortcut("Alt+Enter", R.string.sc_alt_enter, listOf("$ESC\r"), KeyEvent.KEYCODE_ENTER, ALT),
        Shortcut("←", R.string.sc_left, listOf("$ESC[D"), KeyEvent.KEYCODE_DPAD_LEFT),
        Shortcut("→", R.string.sc_right, listOf("$ESC[C"), KeyEvent.KEYCODE_DPAD_RIGHT),
        Shortcut("Home", R.string.sc_home, listOf("$ESC[H"), KeyEvent.KEYCODE_MOVE_HOME),
        Shortcut("End", R.string.sc_end, listOf("$ESC[F"), KeyEvent.KEYCODE_MOVE_END),
        Shortcut("PgUp", R.string.sc_pgup, listOf("$ESC[5~"), KeyEvent.KEYCODE_PAGE_UP),
        Shortcut("PgDn", R.string.sc_pgdn, listOf("$ESC[6~"), KeyEvent.KEYCODE_PAGE_DOWN),
        Shortcut("Ctrl+U", R.string.sc_ctrl_u, listOf(ctrl('u'))),
        Shortcut("Ctrl+K", R.string.sc_ctrl_k, listOf(ctrl('k'))),
        Shortcut("Ctrl+W", R.string.sc_ctrl_w, listOf(ctrl('w'))),
        Shortcut("Ctrl+O", R.string.sc_ctrl_o, listOf(ctrl('o'))),
        Shortcut("Ctrl+T", R.string.sc_ctrl_t, listOf(ctrl('t'))),
        Shortcut("Ctrl+R", R.string.sc_ctrl_r, listOf(ctrl('r'))),
        Shortcut("Ctrl+L", R.string.sc_ctrl_l, listOf(ctrl('l'))),
    )

    /** Slash commands: inserted into the composer only; the user confirms with Enter. */
    val commands: List<Shortcut> = listOf(
        "/new" to R.string.cmd_new,
        "/stop" to R.string.cmd_stop,
        "/retry" to R.string.cmd_retry,
        "/undo" to R.string.cmd_undo,
        "/model" to R.string.cmd_model,
        "/compress" to R.string.cmd_compress,
        "/usage" to R.string.cmd_usage,
        "/status" to R.string.cmd_status,
        "/sessions" to R.string.cmd_sessions,
        "/help" to R.string.cmd_help,
    ).map { (cmd, desc) -> Shortcut(cmd, desc, listOf(cmd)) }
}
