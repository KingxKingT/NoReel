package com.kingxkingt.noreel

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    private lateinit var status: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 36, 28, 24) }
        root.addView(TextView(this).apply { text = "NoReel"; textSize = 30f })
        root.addView(TextView(this).apply { text = "Block likely Reels screens while keeping normal Instagram posts and messages available."; textSize = 16f; setPadding(0,12,0,20) })
        status = TextView(this).apply { textSize = 16f }
        root.addView(status)
        val days = EditText(this).apply { hint = "Days (0–365)"; inputType = 2; setText("1") }
        val hours = EditText(this).apply { hint = "Extra hours (0–23)"; inputType = 2; setText("0") }
        root.addView(days); root.addView(hours)
        root.addView(Button(this).apply { text = "Enable Accessibility"; setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } })
        root.addView(Button(this).apply {
            text = "Start commitment"
            setOnClickListener {
                val d = days.text.toString().toLongOrNull() ?: 0L
                val h = hours.text.toString().toLongOrNull() ?: 0L
                if (d !in 0..365 || h !in 0..23 || (d == 0L && h == 0L)) {
                    Toast.makeText(this@MainActivity, "Choose 1 hour to 365 days.", Toast.LENGTH_LONG).show()
                } else {
                    getSharedPreferences("noreel", MODE_PRIVATE).edit().putLong("until", System.currentTimeMillis() + (d * 24 + h) * 3_600_000L).apply()
                    refresh()
                }
            }
        })
        root.addView(Button(this).apply { text = "End commitment"; setOnClickListener { getSharedPreferences("noreel", MODE_PRIVATE).edit().remove("until").apply(); refresh() } })
        root.addView(TextView(this).apply { text = "Note: Android cannot make an app impossible to uninstall. Reliable blocking depends on Instagram's current interface."; setPadding(0,20,0,0) })
        setContentView(ScrollView(this).apply { addView(root) })
        refresh()
    }
    private fun refresh() {
        val until = getSharedPreferences("noreel", MODE_PRIVATE).getLong("until", 0L)
        status.text = if (until > System.currentTimeMillis()) "Commitment active until: " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(until)) else "Protection is not active."
    }
}
