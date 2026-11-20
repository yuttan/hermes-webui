package com.hermes.webui

import org.junit.Assert.*
import org.junit.Test

class ChatPageTest {
    private val s = "http://100.124.137.25:8899"

    @Test fun detectsChatPage() {
        assertTrue(ChatPage.isChat(s, "$s/chat"))
        assertTrue(ChatPage.isChat(s, "$s/chat?resume=abc"))
        assertTrue(ChatPage.isChat(s, "$s/chat/"))
        assertTrue(ChatPage.isChat("https://x.ts.net/hermes", "https://x.ts.net/hermes/chat"))
    }

    @Test fun otherPagesAndServersAreNotChat() {
        assertFalse(ChatPage.isChat(s, null))
        assertFalse(ChatPage.isChat(s, "$s/"))
        assertFalse(ChatPage.isChat(s, "$s/sessions"))
        assertFalse(ChatPage.isChat(s, "$s/chatroom"))
        assertFalse(ChatPage.isChat(s, "$s/login?next=%2Fchat"))
        assertFalse(ChatPage.isChat(s, "http://evil.example/chat"))
        assertFalse(ChatPage.isChat(s, "about:blank"))
    }
}
