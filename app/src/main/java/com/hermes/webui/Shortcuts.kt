package com.hermes.webui

import android.view.KeyEvent

/**
 * One entry in the collapsible shortcut panel.
 * [chunks] are raw bytes for the Hermes TUI (sent one chunk at a time, [gapMs] apart).
 * [fallbackKey]/[fallbackMeta] is a native key used on pages without the chat terminal.
 */
data class Shortcut(
    val label: String,
    val description: String,
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
        Shortcut("Esc", "閉じる・取消", listOf(ESC), KeyEvent.KEYCODE_ESCAPE),
        Shortcut("Esc×2", "中断・下書き破棄", listOf(ESC, ESC), gapMs = 150),
        Shortcut("Ctrl+C", "中断・入力クリア", listOf(ctrl('c')), KeyEvent.KEYCODE_C, CTRL),
        Shortcut("Tab", "補完", listOf("\t"), KeyEvent.KEYCODE_TAB),
        Shortcut("Shift+Tab", "YOLO切替", listOf("$ESC[Z"), KeyEvent.KEYCODE_TAB, SHIFT),
        Shortcut("Alt+Enter", "改行", listOf("$ESC\r"), KeyEvent.KEYCODE_ENTER, ALT),
        Shortcut("←", "カーソル左", listOf("$ESC[D"), KeyEvent.KEYCODE_DPAD_LEFT),
        Shortcut("→", "カーソル右", listOf("$ESC[C"), KeyEvent.KEYCODE_DPAD_RIGHT),
        Shortcut("Home", "行頭", listOf("$ESC[H"), KeyEvent.KEYCODE_MOVE_HOME),
        Shortcut("End", "行末", listOf("$ESC[F"), KeyEvent.KEYCODE_MOVE_END),
        Shortcut("PgUp", "前ページ", listOf("$ESC[5~"), KeyEvent.KEYCODE_PAGE_UP),
        Shortcut("PgDn", "次ページ", listOf("$ESC[6~"), KeyEvent.KEYCODE_PAGE_DOWN),
        Shortcut("Ctrl+U", "行頭まで削除", listOf(ctrl('u'))),
        Shortcut("Ctrl+K", "行末まで削除", listOf(ctrl('k'))),
        Shortcut("Ctrl+W", "単語削除", listOf(ctrl('w'))),
        Shortcut("Ctrl+O", "モデル選択", listOf(ctrl('o'))),
        Shortcut("Ctrl+T", "エージェント", listOf(ctrl('t'))),
        Shortcut("Ctrl+R", "作業ドック", listOf(ctrl('r'))),
        Shortcut("Ctrl+L", "再描画", listOf(ctrl('l'))),
    )

    /** Slash commands: inserted into the composer only; the user confirms with Enter. */
    val commands: List<Shortcut> = listOf(
        "/new" to "新しい会話", "/stop" to "停止", "/retry" to "再実行", "/undo" to "取り消し",
        "/model" to "モデル", "/compress" to "圧縮", "/usage" to "使用量", "/status" to "状態",
        "/sessions" to "履歴", "/help" to "ヘルプ",
    ).map { (cmd, desc) -> Shortcut(cmd, desc, listOf(cmd)) }
}
