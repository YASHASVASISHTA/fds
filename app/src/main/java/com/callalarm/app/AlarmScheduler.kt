package com.callalarm.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Uses AlarmManager.setAlarmClock(), which is exact, survives Doze and needs no
 * special "exact alarm" permission.
 */
object AlarmScheduler {
    const val ACTION_FIRE = "com.callalarm.app.ACTION_FIRE"
    const val EXTRA_ID = "alarm_id"

    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun firePendingIntent(ctx: Context, id: Int): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(ctx, id, i, FLAGS)
    }

    /** Schedules the next ring of this alarm and returns the trigger time in millis. */
    fun schedule(ctx: Context, alarm: Alarm): Long {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = alarm.nextTriggerMillis()
        val show = PendingIntent.getActivity(
            ctx, 100000 + alarm.id, Intent(ctx, MainActivity::class.java), FLAGS
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), firePendingIntent(ctx, alarm.id))
        return triggerAt
    }

    fun cancel(ctx: Context, id: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(firePendingIntent(ctx, id))
    }

    fun rescheduleAll(ctx: Context) {
        for (a in AlarmStore(ctx).load()) {
            if (a.enabled) schedule(ctx, a)
        }
    }
}
