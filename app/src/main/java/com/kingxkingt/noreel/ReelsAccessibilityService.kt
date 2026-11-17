package com.kingxkingt.noreel

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Closes Instagram's full-screen Reels viewer as soon as it is detected.
 * The specific pager ID avoids blocking normal feed posts, chat screens and story viewers.
 */
class ReelsAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "NoReel"
        private const val INSTAGRAM = "com.instagram.android"
        private const val CLIPS_PAGER_ID = "com.instagram.android:id/clips_viewer_view_pager"
        private const val TREE_SCAN_MS = 300L
        private const val BACK_RETRY_MS = 650L
        private const val BACK_COOLDOWN_MS = 1_800L
        private const val MAX_BACK_ATTEMPTS = 3
        private const val FULLSCREEN_RATIO = 0.60
        private const val MAX_DEPTH = 28
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastTreeScan = 0L
    private var lastBack = 0L
    private var backAttempts = 0
    private var viewerBeingClosed = false

    private val verifyClose = Runnable {
        if (!viewerBeingClosed) return@Runnable
        if (!findFullscreenPager()) {
            Log.d(TAG, "Reels viewer closed")
            viewerBeingClosed = false
            backAttempts = 0
            return@Runnable
        }
        if (backAttempts >= MAX_BACK_ATTEMPTS) {
            Log.w(TAG, "Reels viewer is still present after retry limit")
            viewerBeingClosed = false
            return@Runnable
        }
        sendBack("retry")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED
            packageNames = arrayOf(INSTAGRAM)
            notificationTimeout = 100
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName?.toString() != INSTAGRAM) return
        if (getSharedPreferences("noreel", MODE_PRIVATE).getLong("until", 0L) <= System.currentTimeMillis()) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val source = event.source ?: return
                try {
                    if (source.viewIdResourceName == CLIPS_PAGER_ID) {
                        closeViewer("Reels pager scroll")
                    } else {
                        scanIfNeeded()
                    }
                } finally {
                    source.recycle()
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> scanForViewer("window change")
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> scanIfNeeded()
        }
    }

    private fun scanIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastTreeScan < TREE_SCAN_MS) return
        scanForViewer("content change")
    }

    private fun scanForViewer(reason: String) {
        lastTreeScan = System.currentTimeMillis()
        if (findFullscreenPager()) closeViewer(reason)
    }

    private fun closeViewer(reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastBack < BACK_COOLDOWN_MS) return
        if (!viewerBeingClosed) {
            viewerBeingClosed = true
            backAttempts = 0
        }
        sendBack(reason)
    }

    private fun sendBack(reason: String) {
        lastBack = System.currentTimeMillis()
        backAttempts += 1
        val success = performGlobalAction(GLOBAL_ACTION_BACK)
        Log.d(TAG, "Closing Reels viewer ($reason), attempt=$backAttempts, dispatched=$success")
        handler.removeCallbacks(verifyClose)
        handler.postDelayed(verifyClose, BACK_RETRY_MS)
    }

    private fun findFullscreenPager(): Boolean {
        val root = rootInActiveWindow ?: return false
        return try { containsPager(root, 0) } finally { root.recycle() }
    }

    private fun containsPager(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (depth > MAX_DEPTH) return false
        if (node.viewIdResourceName == CLIPS_PAGER_ID &&
            node.isVisibleToUser && fillsScreen(node)) return true

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = try { containsPager(child, depth + 1) } finally { child.recycle() }
            if (found) return true
        }
        return false
    }

    private fun fillsScreen(node: AccessibilityNodeInfo): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() <= 0 || rect.height() <= 0) return false
        val metrics = resources.displayMetrics
        val screenArea = metrics.widthPixels.toDouble() * metrics.heightPixels.toDouble()
        val nodeArea = rect.width().toDouble() * rect.height().toDouble()
        return screenArea > 0.0 && nodeArea / screenArea >= FULLSCREEN_RATIO
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(verifyClose)
        super.onDestroy()
    }
}
