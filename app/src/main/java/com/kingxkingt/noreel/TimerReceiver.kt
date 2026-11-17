package com.kingxkingt.noreel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        CommitmentTimer.restoreOrExpire(context)
    }
}
