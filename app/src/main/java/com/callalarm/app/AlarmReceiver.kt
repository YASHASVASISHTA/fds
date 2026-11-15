package com.callalarm.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fired by AlarmManager at the alarm time. Starts the "incoming call". */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1)
        val store = AlarmStore(context)
        val alarm = store.get(id) ?: return
        if (!alarm.enabled) return

        // Repeating alarm: line up the next ring. One-time alarm: switch it off.
        if (alarm.days == 0) {
            alarm.enabled = false
            store.upsert(alarm)
        } else {
            AlarmScheduler.schedule(context, alarm)
        }

        context.startForegroundService(
            AlarmService.startIntent(context, alarm.id, alarm.label, alarm.message)
        )
    }
}
