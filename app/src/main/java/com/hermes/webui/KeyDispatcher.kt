package com.hermes.webui

/** UI-thread queue: deliver once, only after focus completes. */
class KeyDispatcher(
    private val focus: ((Boolean) -> Unit) -> Unit,
    private val deliver: (Int) -> Unit
) {
    private val pending = java.util.ArrayDeque<Int>()
    private var busy = false
    private var generation = 0

    fun send(key: Int) {
        pending.addLast(key)
        drain()
    }

    fun cancel() {
        generation++
        pending.clear()
        busy = false
    }

    private fun drain() {
        if (busy || pending.isEmpty()) return
        busy = true
        val key = pending.removeFirst()
        val token = generation
        focus { ready ->
            if (token != generation) return@focus
            if (ready) deliver(key)
            busy = false
            drain()
        }
    }
}
