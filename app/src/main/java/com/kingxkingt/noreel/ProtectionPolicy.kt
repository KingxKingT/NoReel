package com.kingxkingt.noreel

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager

/** Device Owner-only hardening. Ordinary app installs cannot apply these policies. */
object ProtectionPolicy {
    private fun restrictions(): List<String> = buildList {
        add(UserManager.DISALLOW_APPS_CONTROL)
        add(UserManager.DISALLOW_UNINSTALL_APPS)
        add(UserManager.DISALLOW_SAFE_BOOT)
        add(UserManager.DISALLOW_FACTORY_RESET)
        add(UserManager.DISALLOW_DEBUGGING_FEATURES)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(UserManager.DISALLOW_CONFIG_DATE_TIME)
    }

    fun isDeviceOwner(context: Context): Boolean =
        context.getSystemService(DevicePolicyManager::class.java)?.isDeviceOwnerApp(context.packageName) == true

    fun apply(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        if (!dpm.isDeviceOwnerApp(context.packageName)) return false
        val admin = ComponentName(context, NoReelAdminReceiver::class.java)
        dpm.setUninstallBlocked(admin, context.packageName, true)
        restrictions().forEach { dpm.addUserRestriction(admin, it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val prefs = context.getSharedPreferences("noreel", Context.MODE_PRIVATE)
            if (!prefs.contains("previous_auto_time")) {
                prefs.edit().putBoolean("previous_auto_time", dpm.getAutoTimeEnabled(admin)).apply()
            }
            dpm.setAutoTimeEnabled(admin, true)
        }
        return true
    }

    fun release(context: Context) {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return
        if (!dpm.isDeviceOwnerApp(context.packageName)) return
        val admin = ComponentName(context, NoReelAdminReceiver::class.java)
        restrictions().forEach { runCatching { dpm.clearUserRestriction(admin, it) } }
        runCatching { dpm.setUninstallBlocked(admin, context.packageName, false) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val prefs = context.getSharedPreferences("noreel", Context.MODE_PRIVATE)
            val old = prefs.getBoolean("previous_auto_time", true)
            runCatching { dpm.setAutoTimeEnabled(admin, old) }
            prefs.edit().remove("previous_auto_time").apply()
        }
    }
}
