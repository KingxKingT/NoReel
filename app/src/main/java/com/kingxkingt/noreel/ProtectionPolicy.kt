package com.kingxkingt.noreel

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.provider.Settings

/** Device Owner-only hardening. Ordinary app installs cannot apply these policies. */
object ProtectionPolicy {
    private const val PREFS = "noreel"
    private const val KEY_PREVIOUS_AUTO_TIME = "previous_auto_time"

    private fun restrictions(): List<String> = buildList {
        add(UserManager.DISALLOW_APPS_CONTROL)
        add(UserManager.DISALLOW_UNINSTALL_APPS)
        add(UserManager.DISALLOW_SAFE_BOOT)
        add(UserManager.DISALLOW_FACTORY_RESET)
        add(UserManager.DISALLOW_DEBUGGING_FEATURES)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            add(UserManager.DISALLOW_CONFIG_DATE_TIME)
        }
    }

    fun isAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        return dpm.isAdminActive(ComponentName(context, NoReelAdminReceiver::class.java))
    }

    fun isDeviceOwner(context: Context): Boolean =
        context.getSystemService(DevicePolicyManager::class.java)?.isDeviceOwnerApp(context.packageName) == true

    fun apply(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        if (!dpm.isDeviceOwnerApp(context.packageName)) return false

        val admin = ComponentName(context, NoReelAdminReceiver::class.java)
        dpm.setUninstallBlocked(admin, context.packageName, true)
        restrictions().forEach { dpm.addUserRestriction(admin, it) }

        // Keep the system clock network-synchronized during a commitment. This global
        // setting API works on Android 10 as well as newer releases.
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_PREVIOUS_AUTO_TIME)) {
            val oldValue = Settings.Global.getString(context.contentResolver, Settings.Global.AUTO_TIME)
            prefs.edit().putString(KEY_PREVIOUS_AUTO_TIME, oldValue ?: "1").apply()
        }
        dpm.setGlobalSetting(admin, Settings.Global.AUTO_TIME, "1")
        return true
    }

    fun release(context: Context) {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return
        if (!dpm.isDeviceOwnerApp(context.packageName)) return

        val admin = ComponentName(context, NoReelAdminReceiver::class.java)
        restrictions().forEach { restriction ->
            runCatching { dpm.clearUserRestriction(admin, restriction) }
        }
        runCatching { dpm.setUninstallBlocked(admin, context.packageName, false) }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val oldAutoTime = prefs.getString(KEY_PREVIOUS_AUTO_TIME, null)
        if (oldAutoTime != null) {
            runCatching { dpm.setGlobalSetting(admin, Settings.Global.AUTO_TIME, oldAutoTime) }
            prefs.edit().remove(KEY_PREVIOUS_AUTO_TIME).apply()
        }
    }
}
