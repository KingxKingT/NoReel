package com.kingxkingt.noreel

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var detail: TextView
    private lateinit var adminButton: Button
    private lateinit var daysInput: EditText
    private lateinit var hoursInput: EditText
    private lateinit var startButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
    }

    override fun onResume() {
        super.onResume()
        CommitmentTimer.restoreOrExpire(this)
        if (::status.isInitialized) refreshScreen()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 34, 28, 24)
        }

        root.addView(TextView(this).apply {
            text = "NoReel"
            textSize = 32f
        })
        root.addView(TextView(this).apply {
            text = "Stay off Instagram Reels without blocking normal posts and messages."
            textSize = 16f
            setPadding(0, 10, 0, 22)
        })

        status = TextView(this).apply { textSize = 18f }
        detail = TextView(this).apply { textSize = 14f; setPadding(0, 8, 0, 18) }
        root.addView(status)
        root.addView(detail)

        adminButton = Button(this).apply {
            text = "Enable uninstall protection"
            setOnClickListener { requestDeviceAdmin() }
        }
        root.addView(adminButton)

        root.addView(Button(this).apply {
            text = "Open Accessibility Settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })

        daysInput = EditText(this).apply {
            hint = "Days (0–365)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("1")
        }
        hoursInput = EditText(this).apply {
            hint = "Extra hours (0–23)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("0")
        }
        root.addView(daysInput)
        root.addView(hoursInput)

        startButton = Button(this).apply {
            text = "Start commitment"
            setOnClickListener { beginCommitment() }
        }
        root.addView(startButton)

        root.addView(TextView(this).apply {
            text = "Device Admin adds a warning and requires deactivation before uninstalling. Device Owner mode adds stronger Android restrictions, but needs a fresh-device setup with ADB. No app can prevent every reset or privileged bypass."
            textSize = 13f
            setPadding(0, 22, 0, 0)
        })

        setContentView(ScrollView(this).apply { addView(root) })
        refreshScreen()
    }

    private fun requestDeviceAdmin() {
        val admin = ComponentName(this, NoReelAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "NoReel uses Device Admin to make uninstalling harder during your commitment. You can deactivate it in Android security settings."
            )
        startActivity(intent)
    }

    private fun beginCommitment() {
        val days = daysInput.text.toString().toLongOrNull() ?: 0L
        val hours = hoursInput.text.toString().toLongOrNull() ?: 0L
        if (days !in 0..365 || hours !in 0..23 || (days == 0L && hours == 0L)) {
            Toast.makeText(this, "Choose at least 1 hour, up to 365 days.", Toast.LENGTH_LONG).show()
            return
        }

        if (!ProtectionPolicy.isAdminActive(this)) {
            Toast.makeText(this, "Enable uninstall protection first.", Toast.LENGTH_LONG).show()
            requestDeviceAdmin()
            return
        }

        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "Enable NoReel Accessibility first.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        val duration = (days * 24L + hours) * 60L * 60L * 1000L
        val until = CommitmentTimer.start(this, duration)
        val hardened = runCatching { ProtectionPolicy.apply(this) }.getOrDefault(false)
        Toast.makeText(
            this,
            if (hardened) "Commitment started. Device Owner restrictions applied."
            else "Commitment started. Basic uninstall protection is active.",
            Toast.LENGTH_LONG
        ).show()
        refreshScreen(until)
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = ComponentName(this, ReelsAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun refreshScreen(untilOverride: Long? = null) {
        val until = untilOverride ?: CommitmentTimer.activeUntil(this)
        val active = until > System.currentTimeMillis()
        val adminActive = ProtectionPolicy.isAdminActive(this)
        val owner = ProtectionPolicy.isDeviceOwner(this)

        status.text = if (active) "Commitment active" else "Protection is off"
        detail.text = buildString {
            if (active) {
                append("Ends: ")
                append(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(until)))
            } else {
                append("Set a commitment to start blocking Reels.")
            }
            append("\n")
            append(if (adminActive) "Device Admin: enabled" else "Device Admin: not enabled")
            append("\n")
            append(if (owner) "Device Owner: enabled (stronger restrictions)" else "Device Owner: not set up")
        }

        adminButton.visibility = if (adminActive) View.GONE else View.VISIBLE
        daysInput.visibility = if (active) View.GONE else View.VISIBLE
        hoursInput.visibility = if (active) View.GONE else View.VISIBLE
        startButton.visibility = if (active) View.GONE else View.VISIBLE
    }
}
