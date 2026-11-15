package com.callalarm.app

import android.content.Context
import android.text.format.DateFormat
import java.util.Calendar
import java.util.Locale

/**
 * One alarm. [days] is a bitmask: bit 0 = Monday ... bit 6 = Sunday.
 * days == 0 means "ring once".
 */
data class Alarm(
    val id: Int,
    var hour: Int,
    var minute: Int,
    var label: String,
    var message: String,
    var days: Int,
    var enabled: Boolean
) {
    fun nextTriggerMillis(from: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        for (offset in 0..7) {
            cal.timeInMillis = from
            cal.add(Calendar.DAY_OF_YEAR, offset)
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            if (cal.timeInMillis <= from) continue
            if (days == 0) return cal.timeInMillis
            val idx = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Mon=0 ... Sun=6
            if ((days and (1 shl idx)) != 0) return cal.timeInMillis
        }
        return from + 24L * 60L * 60L * 1000L
    }
}

object Fmt {
    private val dayNames = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun time(ctx: Context, hour: Int, minute: Int): String {
        return if (DateFormat.is24HourFormat(ctx)) {
            String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
        } else {
            val h = if (hour % 12 == 0) 12 else hour % 12
            String.format(Locale.getDefault(), "%d:%02d %s", h, minute, if (hour < 12) "AM" else "PM")
        }
    }

    fun days(mask: Int): String = when (mask) {
        0 -> "Once"
        127 -> "Every day"
        31 -> "Weekdays"
        96 -> "Weekends"
        else -> dayNames.filterIndexed { i, _ -> (mask and (1 shl i)) != 0 }.joinToString(", ")
    }

    fun until(ms: Long): String {
        val totalMin = (ms + 59_999L) / 60_000L
        val d = totalMin / 1440
        val h = (totalMin % 1440) / 60
        val m = totalMin % 60
        val parts = mutableListOf<String>()
        if (d > 0) parts.add("${d}d")
        if (h > 0) parts.add("${h}h")
        if (m > 0 || parts.isEmpty()) parts.add("${m}m")
        return parts.joinToString(" ")
    }
}
