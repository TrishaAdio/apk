package com.upimonitor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Keeps the most recent forward attempts so the UI can show what happened. */
object LogStore {
    private const val MAX = 50

    fun add(context: Context, line: String) {
        val sp = context.getSharedPreferences("upi_monitor_log", Context.MODE_PRIVATE)
        val arr = JSONArray(sp.getString("log", "[]"))
        val entry = JSONObject()
            .put("ts", System.currentTimeMillis())
            .put("msg", line)
        // prepend newest
        val next = JSONArray()
        next.put(entry)
        for (i in 0 until minOf(arr.length(), MAX - 1)) next.put(arr.get(i))
        sp.edit().putString("log", next.toString()).apply()
    }

    fun entries(context: Context): List<String> {
        val sp = context.getSharedPreferences("upi_monitor_log", Context.MODE_PRIVATE)
        val arr = JSONArray(sp.getString("log", "[]"))
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val t = android.text.format.DateFormat.format("MM-dd HH:mm:ss", o.getLong("ts"))
            out.add("$t  ${o.getString("msg")}")
        }
        return out
    }

    fun clear(context: Context) {
        context.getSharedPreferences("upi_monitor_log", Context.MODE_PRIVATE)
            .edit().remove("log").apply()
    }
}
