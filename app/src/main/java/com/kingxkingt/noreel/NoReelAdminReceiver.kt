package com.kingxkingt.noreel

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class NoReelAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Disabling NoReel device administration removes its basic uninstall protection. Your active commitment may no longer be protected."
    }
}
