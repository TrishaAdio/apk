package com.upimonitor

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Posts a received SMS to the configured webhook. Uses plain HttpURLConnection. */
object Forwarder {

    /** Returns true if the message matched the filters and was accepted by the server. */
    fun forward(context: Context, sender: String, body: String, sentAtMillis: Long): Boolean {
        val prefs = Prefs(context)
        if (!prefs.isConfigured()) {
            LogStore.add(context, "SKIP no webhook URL set")
            return false
        }
        if (!matches(prefs, sender, body)) {
            return false
        }

        val payload = JSONObject()
            .put("sender", sender)
            .put("body", body)
            .put("sentAt", iso(sentAtMillis))
            .put("deviceId", android.os.Build.MODEL ?: "android")
            .toString()

        var lastError = ""
        repeat(3) { attempt ->
            try {
                val ok = post(prefs.webhookUrl, prefs.apiKey, payload)
                if (ok) {
                    LogStore.add(context, "OK forwarded Rs? from $sender")
                    return true
                }
                lastError = "HTTP error"
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
            }
            Thread.sleep((500L * (attempt + 1)))
        }
        LogStore.add(context, "FAIL $lastError")
        return false
    }

    private fun matches(prefs: Prefs, sender: String, body: String): Boolean {
        val senders = prefs.senderFilter.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val keywords = prefs.keywordFilter.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        val senderOk = senders.isEmpty() ||
            senders.any { sender.contains(it, ignoreCase = true) }
        val keywordOk = keywords.isEmpty() ||
            keywords.any { body.contains(it, ignoreCase = true) }

        // Require a keyword match; sender filter is an additional narrowing when set.
        return keywordOk && senderOk
    }

    private fun post(urlStr: String, apiKey: String, json: String): Boolean {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (apiKey.isNotBlank()) conn.setRequestProperty("X-Api-Key", apiKey)

            val out: OutputStream = conn.outputStream
            out.write(json.toByteArray(Charsets.UTF_8))
            out.flush()
            out.close()

            val code = conn.responseCode
            // Drain the stream so the connection can be reused/closed cleanly.
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            stream?.let { BufferedReader(InputStreamReader(it)).use { r -> r.readText() } }
            return code in 200..299
        } finally {
            conn.disconnect()
        }
    }

    private fun iso(millis: Long): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
        return fmt.format(java.util.Date(millis))
    }
}
