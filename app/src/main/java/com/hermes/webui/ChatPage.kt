package com.hermes.webui

object ChatPage {
    /**
     * True when [url] is the dashboard's Chat page (`<server>/chat`, with or without `?resume=` etc.).
     * [serverUrl] may carry a path prefix for dashboards behind a reverse proxy.
     */
    fun isChat(serverUrl: String, url: String?): Boolean {
        if (url == null || !url.startsWith(serverUrl)) return false
        val rest = url.substring(serverUrl.length).substringBefore('#').substringBefore('?')
        return rest == "/chat" || rest.startsWith("/chat/")
    }
}
