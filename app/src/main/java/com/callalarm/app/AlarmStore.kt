package com.callalarm.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Saves the alarm list as JSON in SharedPreferences. */
class AlarmStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("call_alarms", Context.MODE_PRIVATE)

    fun load(): MutableList<Alarm> {
        val out = mutableListOf<Alarm>()
        try {
            val arr = JSONArray(prefs.getString("list", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Alarm(
                        id = o.getInt("id"),
                        hour = o.getInt("hour"),
                        minute = o.getInt("minute"),
                        label = o.optString("label", "Wake Up"),
                        message = o.optString("message", ""),
                        days = o.optInt("days", 0),
                        enabled = o.optBoolean("enabled", true)
                    )
                )
            }
        } catch (_: Exception) {
            // corrupted data: start fresh
        }
        return out
    }

    fun save(list: List<Alarm>) {
        val arr = JSONArray()
        for (a in list) {
            arr.put(
                JSONObject()
                    .put("id", a.id)
                    .put("hour", a.hour)
                    .put("minute", a.minute)
                    .put("label", a.label)
                    .put("message", a.message)
                    .put("days", a.days)
                    .put("enabled", a.enabled)
            )
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    fun get(id: Int): Alarm? = load().firstOrNull { it.id == id }

    fun nextId(list: List<Alarm>): Int = (list.maxOfOrNull { it.id } ?: 0) + 1

    fun upsert(alarm: Alarm) {
        val list = load()
        val idx = list.indexOfFirst { it.id == alarm.id }
        if (idx >= 0) list[idx] = alarm else list.add(alarm)
        save(list)
    }

    fun delete(id: Int) {
        save(load().filter { it.id != id })
    }
}
