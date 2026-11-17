package com.kingxkingt.noreel

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var days: EditText
    private lateinit var hours: EditText
    private lateinit var startButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 36, 28, 24) }
        root.addView(TextView(this).apply { text = "NoReel"; textSize = 30f })
        root.addView(TextView(this).apply {
            text = "Blocks selected likely Reels screens while keeping normal Instagram posts and messages available."
            textSize = 16f; setPadding(0, 12, 0, 20)
        })
        status = TextView(this).apply { textSize = 16f }
        root.addView(status)
        days = EditText(this).apply { hint = "Days (0–365)"; inputType = 2; setText("1") }
        hours = EditText(this).apply { hint = "Extra hours (0–23)"; inputType = 2; setText("0") }
        root.addView(days); root.addView(hours)
        root.addView(Button(this).apply {
            text = "Enable Accessibility"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        startButton = Button(this).apply {
            text = "Start commitment"
            setOnClickListener {
                val d = days.text.toString().toLongOrNull() ?: 0L
                val h = hours.text.toString().toLongOrNull() ?: 0L
                if (d !in 0..365 || h !in 0..23 || (d == 0L && h == 0L)) {
                    Toast.makeText(this@MainActivity, "Choose at least 1 hour, up to 365 days.", Toast.LENGTH_LONG).show()
                } else {
                    val until = System.currentTimeMillis() + (d * 24 + h) * 3_600_000L
                    getSharedPreferences("noreel", MODE_PRIVATE).edit().putLong("until", until).apply()
                    refresh()
                }
            }
        }
        root.addView(startButton)
        root.addView(TextView(this).apply {
            text = "Commitment cannot be cancelled from this screen. Android still allows app data clearing, uninstalling, or a factory reset unless the device is specially provisioned. Reel detection can change when Instagram updates."
            setPadding(0, 20, 0, 0)
        })
        setContentView(ScrollView(this).apply { addView(root) })
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refresh()
    }

    private fun refresh() {
        val until = getSharedPreferences("noreel", MODE_PRIVATE).getLong("until", 0L)
        val active = until > System.currentTimeMillis()
        status.text = if (active) {
            "Commitment active until: " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(until))
        } else "Protection is not active."
        days.visibility = if (active) View.GONE else View.VISIBLE
        hours.visibility = if (active) View.GONE else View.VISIBLE
        startButton.visibility = if (active) View.GONE else View.VISIBLE
    }
}
