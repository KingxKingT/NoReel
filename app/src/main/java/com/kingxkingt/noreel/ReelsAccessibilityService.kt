package com.kingxkingt.noreel

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ReelsAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName?.toString() != "com.instagram.android") return
        val until = getSharedPreferences("noreel", MODE_PRIVATE).getLong("until", 0L)
        if (until <= System.currentTimeMillis()) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val root = rootInActiveWindow ?: return
        val clues = ArrayList<String>()
        collectText(root, clues, 0)
        val text = clues.joinToString(" ").lowercase()
        // Avoid treating the ordinary "Reels" navigation tab as a viewer.
        val viewerClue = listOf("reel viewer", "reels viewer", "close reels", "reel video", "video player")
            .any { text.contains(it) }
        val strongCombination = text.contains("sponsored") && text.contains("audio") && text.contains("reels")
        if (viewerClue || strongCombination) performGlobalAction(GLOBAL_ACTION_BACK)
    }
    private fun collectText(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int) {
        if (depth > 18 || out.size > 350) return
        node.text?.toString()?.let { if (it.isNotBlank()) out.add(it) }
        node.contentDescription?.toString()?.let { if (it.isNotBlank()) out.add(it) }
        for (i in 0 until node.childCount) node.getChild(i)?.let { child ->
            collectText(child, out, depth + 1)
            child.recycle()
        }
    }
    override fun onInterrupt() {}
}
