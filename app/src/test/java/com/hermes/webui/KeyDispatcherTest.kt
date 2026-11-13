package com.hermes.webui

import org.junit.Assert.*
import org.junit.Test

class KeyDispatcherTest {
    @Test fun waitsForFocusAndDeliversRepeatedKeysOnceInOrder() {
        val callbacks = java.util.ArrayDeque<(Boolean) -> Unit>()
        val delivered = mutableListOf<Int>()
        val dispatcher = KeyDispatcher({ callbacks.add(it) }, { delivered.add(it) })
        dispatcher.send(20)
        dispatcher.send(20)
        dispatcher.send(66)
        assertTrue(delivered.isEmpty())
        assertEquals(1, callbacks.size)
        callbacks.removeFirst()(true)
        assertEquals(listOf(20), delivered)
        callbacks.removeFirst()(true)
        callbacks.removeFirst()(true)
        assertEquals(listOf(20, 20, 66), delivered)
    }
    @Test fun missingInputAndCanceledOperationsSendNothing() {
        val callbacks = java.util.ArrayDeque<(Boolean) -> Unit>()
        val delivered = mutableListOf<Int>()
        val dispatcher = KeyDispatcher({ callbacks.add(it) }, { delivered.add(it) })
        dispatcher.send(66)
        callbacks.removeFirst()(false)
        assertTrue(delivered.isEmpty())
        dispatcher.send(20)
        dispatcher.cancel()
        callbacks.removeFirst()(true)
        assertTrue(delivered.isEmpty())
    }
}
