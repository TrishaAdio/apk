package com.upimonitor

import android.content.Context

/** Simple SharedPreferences-backed settings store. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("upi_monitor", Context.MODE_PRIVATE)

    var webhookUrl: String
        get() = sp.getString(KEY_URL, "") ?: ""
        set(v) = sp.edit().putString(KEY_URL, v.trim()).apply()

    var apiKey: String
        get() = sp.getString(KEY_API, "") ?: ""
        set(v) = sp.edit().putString(KEY_API, v.trim()).apply()

    /** Comma-separated sender substrings; blank = don't filter by sender. */
    var senderFilter: String
        get() = sp.getString(KEY_SENDERS, "BOB") ?: "BOB"
        set(v) = sp.edit().putString(KEY_SENDERS, v.trim()).apply()

    /** Comma-separated keywords the body must contain (any of); blank = forward all. */
    var keywordFilter: String
        get() = sp.getString(KEY_KEYWORDS, "credited,UPI Ref No") ?: "credited,UPI Ref No"
        set(v) = sp.edit().putString(KEY_KEYWORDS, v.trim()).apply()

    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, false)
        set(v) = sp.edit().putBoolean(KEY_ENABLED, v).apply()

    fun isConfigured(): Boolean = webhookUrl.isNotBlank()

    companion object {
        private const val KEY_URL = "webhook_url"
        private const val KEY_API = "api_key"
        private const val KEY_SENDERS = "sender_filter"
        private const val KEY_KEYWORDS = "keyword_filter"
        private const val KEY_ENABLED = "enabled"
    }
}
