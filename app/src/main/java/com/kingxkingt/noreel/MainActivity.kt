package com.kingxkingt.noreel

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val page = Color.rgb(247, 248, 244)
    private val surface = Color.WHITE
    private val ink = Color.rgb(28, 38, 32)
    private val muted = Color.rgb(111, 122, 115)
    private val accent = Color.rgb(48, 104, 75)
    private val accentSoft = Color.rgb(229, 240, 231)
    private val border = Color.rgb(229, 234, 227)

    private lateinit var statusBadge: TextView
    private lateinit var statusTitle: TextView
    private lateinit var statusDescription: TextView
    private lateinit var adminStatus: TextView
    private lateinit var accessibilityStatus: TextView
    private lateinit var commitmentCard: LinearLayout
    private lateinit var diagnostics: TextView
    private lateinit var days: EditText
    private lateinit var hours: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = page
        window.navigationBarColor = page
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            (if (android.os.Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
        buildScreen()
    }

    override fun onResume() {
        super.onResume()
        CommitmentTimer.restoreOrExpire(this)
        if (::statusTitle.isInitialized) refreshScreen()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun rounded(color: Int, radius: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            typeface = Typeface.create(
                "sans-serif",
                if (bold) Typeface.BOLD else Typeface.NORMAL
            )
            includeFontPadding = false
        }

    private fun addSpace(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun card(backgroundColor: Int = surface): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(17), dp(18), dp(17))
            background = rounded(backgroundColor, 22, if (backgroundColor == surface) border else null)
        }

    private fun primaryAction(label: String, onClick: () -> Unit): TextView =
        text(label, 15f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(55)
            background = rounded(accent, 17)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun numberField(hintText: String, initialValue: String): EditText =
        EditText(this).apply {
            hint = hintText
            setText(initialValue)
            textSize = 18f
            setTextColor(ink)
            setHintTextColor(muted)
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(Color.rgb(247, 248, 245), 14, border)
        }

    private fun setupStep(
        titleText: String,
        detailText: String,
        onClick: () -> Unit
    ): Pair<LinearLayout, TextView> {
        val outer = card()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(text(titleText, 15f, ink, true))
        val detail = text(detailText, 12f, muted)
        detail.setPadding(0, dp(5), dp(8), 0)
        copy.addView(detail, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val state = text("ENABLE", 10f, accent, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(11), dp(8), dp(11), dp(8))
            background = rounded(accentSoft, 20)
            letterSpacing = 0.04f
        }
        row.addView(state, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        outer.addView(row)
        outer.isClickable = true
        outer.isFocusable = true
        outer.setOnClickListener { onClick() }
        return Pair(outer, state)
    }

    private fun buildScreen() {
        val scroll = ScrollView(this).apply {
            setFillViewport(true)
            clipToPadding = false
            setBackgroundColor(page)
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(23), dp(22), dp(23), dp(32))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(text("NoReel", 21f, ink, true), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ))
        statusBadge = text("NOT ACTIVE", 10f, muted, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(Color.rgb(237, 239, 234), 30)
            letterSpacing = 0.04f
        }
        header.addView(statusBadge)
        body.addView(header)

        addSpace(body, 32)
        body.addView(text("Keep Instagram.\nSkip the endless scroll.", 32f, ink, true).apply {
            setLineSpacing(dp(2).toFloat(), 1f)
        })
        addSpace(body, 10)
        body.addView(text(
            "Keep your messages and regular posts. Leave the endless Reels feed behind.",
            14f,
            muted
        ))

        addSpace(body, 25)
        val protectionCard = card(accentSoft)
        protectionCard.addView(text("YOUR PROTECTION", 10f, accent, true).apply {
            letterSpacing = 0.11f
        })
        statusTitle = text("Protection is not active", 21f, ink, true).apply {
            setPadding(0, dp(12), 0, dp(7))
        }
        statusDescription = text(
            "Finish the two setup steps, then choose how long you want to stay Reels-free.",
            13f,
            muted
        )
        statusDescription.setLineSpacing(dp(3).toFloat(), 1f)
        protectionCard.addView(statusTitle)
        protectionCard.addView(statusDescription)
        body.addView(protectionCard)

        addSpace(body, 12)
        val diagnosticsCard = card()
        diagnosticsCard.addView(text("BLOCKING CHECK", 10f, muted, true).apply {
            letterSpacing = 0.10f
        })
        diagnostics = text("Waiting for Instagram activity", 12f, muted).apply {
            setPadding(0, dp(8), 0, 0)
            setLineSpacing(dp(3).toFloat(), 1f)
        }
        diagnosticsCard.addView(diagnostics)
        body.addView(diagnosticsCard)

        addSpace(body, 26)
        body.addView(text("One-time setup", 18f, ink, true))
        addSpace(body, 11)
        val admin = setupStep(
            "Uninstall guard (optional)",
            "Extra protection against removal. Not required for Reels blocking."
        ) { requestDeviceAdmin() }
        adminStatus = admin.second
        body.addView(admin.first)
        addSpace(body, 9)
        val accessibility = setupStep(
            "Accessibility access",
            "Lets NoReel recognise and close Instagram's Reels player."
        ) { openAccessibilitySettings() }
        accessibilityStatus = accessibility.second
        body.addView(accessibility.first)

        addSpace(body, 26)
        commitmentCard = card()
        commitmentCard.addView(text("Choose your commitment", 18f, ink, true))
        commitmentCard.addView(text(
            "Set a time window. Reels blocking stays on until it ends.",
            12f,
            muted
        ).apply { setPadding(0, dp(6), 0, dp(15)) })

        val fields = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val dayColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        dayColumn.addView(text("DAYS", 10f, muted, true).apply { letterSpacing = 0.08f })
        days = numberField("Days", "1")
        dayColumn.addView(days, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(51)
        ))
        val hourColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        hourColumn.addView(text("HOURS", 10f, muted, true).apply { letterSpacing = 0.08f })
        hours = numberField("Hours", "0")
        hourColumn.addView(hours, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(51)
        ))
        fields.addView(dayColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(7)
        })
        fields.addView(hourColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(7)
        })
        commitmentCard.addView(fields)
        val start = primaryAction("Start my commitment") { beginCommitment() }
        commitmentCard.addView(start, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(55)
        ).apply { topMargin = dp(17) })
        body.addView(commitmentCard)

        addSpace(body, 17)
        body.addView(text(
            "A note about DMs: opening a Reel in Instagram's full-screen player is blocked and returns you to the conversation. Inline previews may still appear inside a chat.",
            11f,
            muted
        ).apply {
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(2), dp(2), dp(2), 0)
        })

        scroll.addView(body)
        setContentView(scroll)
        refreshScreen()
    }

    private fun enabled(): Boolean {
        val expected = ComponentName(this, ReelsAccessibilityService::class.java).flattenToString()
        val list = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return list.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun requestDeviceAdmin() {
        val admin = ComponentName(this, NoReelAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "NoReel adds an extra confirmation step before its uninstall protection can be disabled."
            )
        startActivity(intent)
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun beginCommitment() {
        val dayCount = days.text.toString().toLongOrNull() ?: 0L
        val hourCount = hours.text.toString().toLongOrNull() ?: 0L
        if (dayCount !in 0..365 || hourCount !in 0..23 ||
            (dayCount == 0L && hourCount == 0L)
        ) {
            Toast.makeText(this, "Choose at least 1 hour, up to 365 days.", Toast.LENGTH_LONG).show()
            return
        }

        if (!enabled()) {
            Toast.makeText(this, "Enable NoReel in Accessibility settings first.", Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return
        }

        val until = CommitmentTimer.start(this, (dayCount * 24L + hourCount) * 3600000L)
        val hardened = if (ProtectionPolicy.isAdminActive(this)) {
            runCatching { ProtectionPolicy.apply(this) }.getOrDefault(false)
        } else false
        Toast.makeText(
            this,
            if (hardened) "Commitment started. Extra device restrictions are active."
            else "Commitment started. Reels blocking is active.",
            Toast.LENGTH_LONG
        ).show()
        refreshScreen(until)
    }

    private fun refreshScreen(overrideUntil: Long? = null) {
        val until = overrideUntil ?: CommitmentTimer.activeUntil(this)
        val active = until > System.currentTimeMillis()
        val admin = ProtectionPolicy.isAdminActive(this)
        val accessibility = enabled()
        val owner = ProtectionPolicy.isDeviceOwner(this)
        val diagnosticPrefs = getSharedPreferences("noreel", MODE_PRIVATE)
        val blockCount = diagnosticPrefs.getInt("block_count", 0)
        val serviceConnected = diagnosticPrefs.getBoolean("service_connected", false)
        val lastDetection = diagnosticPrefs.getString("last_detection", "No Reels screen detected yet")
            ?: "No Reels screen detected yet"
        val detectionAt = diagnosticPrefs.getLong("last_detection_at", 0L)
        val detectionTime = if (detectionAt > 0L) {
            java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                .format(java.util.Date(detectionAt))
        } else {
            "Not yet"
        }
        diagnostics.text =
            "Accessibility enabled: " + if (accessibility) "YES" else "NO" +
            "\\nService connected: " + if (serviceConnected) "YES" else "NO" +
            "\\nBlocks detected: " + blockCount +
            "\\nLast check: " + lastDetection +
            "\\nTime: " + detectionTime

        adminStatus.text = if (admin) "READY" else "ENABLE"
        adminStatus.setTextColor(if (admin) accent else muted)
        adminStatus.background = rounded(if (admin) accentSoft else Color.rgb(237, 239, 234), 20)

        accessibilityStatus.text = if (accessibility) "READY" else "ENABLE"
        accessibilityStatus.setTextColor(if (accessibility) accent else muted)
        accessibilityStatus.background = rounded(
            if (accessibility) accentSoft else Color.rgb(237, 239, 234),
            20
        )

        statusBadge.text = when {
            active && accessibility && serviceConnected -> "ACTIVE"
            active -> "CHECK SETUP"
            accessibility -> "READY"
            else -> "NOT ACTIVE"
        }
        statusBadge.setTextColor(if (active && accessibility) accent else muted)
        statusBadge.background = rounded(
            if (active && accessibility) accentSoft else Color.rgb(237, 239, 234),
            30
        )

        if (active) {
            statusTitle.text = if (accessibility) "Your commitment is active" else "Blocking needs attention"
            val end = java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.MEDIUM,
                java.text.DateFormat.SHORT
            ).format(java.util.Date(until))
            statusDescription.text = if (accessibility) {
                "The Reels tab and full-screen Reels player are being blocked until " + end +
                    if (owner) ". Device-owner restrictions are also active." else "."
            } else {
                "Your timer is still running, but Android accessibility is off. Turn NoReel back on in Accessibility settings."
            }
            commitmentCard.visibility = View.GONE
        } else {
            statusTitle.text = if (accessibility) "You're ready to focus" else "Protection is not active"
            statusDescription.text = if (accessibility) {
                "Accessibility is enabled. The uninstall guard is optional. Choose a commitment to start."
            } else {
                "Enable NoReel in Android Accessibility settings. The uninstall guard is optional."
            }
            commitmentCard.visibility = View.VISIBLE
        }
    }
}
