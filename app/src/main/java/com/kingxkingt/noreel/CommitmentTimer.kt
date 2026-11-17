package com.kingxkingt.noreel

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object CommitmentTimer {
    private const val PREFS = "noreel"
    private const val KEY_UNTIL = "until"

    fun start(context: Context, durationMillis: Long): Long {
        require(durationMillis > 0L)
        val until = System.currentTimeMillis() + durationMillis
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_UNTIL, until).apply()
        schedule(context, until)
        return until
    }

    fun activeUntil(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_UNTIL, 0L)

    fun isActive(context: Context): Boolean = activeUntil(context) > System.currentTimeMillis()

    fun restoreOrExpire(context: Context) {
        val until = activeUntil(context)
        if (until <= 0L) return
        if (until <= System.currentTimeMillis()) expire(context) else schedule(context, until)
    }

    fun expire(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_UNTIL).apply()
        ProtectionPolicy.release(context)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(pendingIntent(context))
    }

    private fun schedule(context: Context, until: Long) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, TimerReceiver::class.java)
        return PendingIntent.getBroadcast(context, 2201, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
