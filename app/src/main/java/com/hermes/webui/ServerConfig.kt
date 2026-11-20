package com.hermes.webui

import android.content.Context

/** The dashboard address chosen on this device (SharedPreferences), with an optional build-time default. */
object ServerConfig {
    private const val PREFS = "server"
    private const val KEY_URL = "url"

    /** Saved address, else the build-time `hermes.url` (own builds only), else null = first run. */
    fun get(context: Context): String? {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_URL, null)
        if (!saved.isNullOrEmpty()) return saved
        val built = BuildConfig.DEFAULT_URL
        return (ServerUrl.parse(built) as? ServerUrl.Result.Ok)?.url
    }

    fun set(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_URL, url).apply()
    }
}
