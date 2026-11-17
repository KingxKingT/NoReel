package com.kingxkingt.noreel

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Detects Instagram's full-screen Reels pager and blocks navigation to another Reel. */
class ReelsAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "NoReel"
        private const val INSTAGRAM = "com.instagram.android"
        private const val CLIPS_PAGER_ID = "com.instagram.android:id/clips_viewer_view_pager"
        private const val TREE_SCAN_MS = 400L
        private const val OPEN_SETTLE_MS = 450L
        private const val OPEN_REPOSITION_MS = 120L
        private const val BACK_VERIFY_MS = 650L
        private const val FULLSCREEN_RATIO = 0.60
        private const val MAX_DEPTH = 28
    }

    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var page = -1
    private var armedAt = 0L
    private var lastTreeScan = 0L
    private var lastBack = 0L
    private var lastPagerEvent = 0L
    private var pendingPage = -1

    private val verifyBack = Runnable {
        if (findFullscreenPager()) {
            active = true
            page = pendingPage
            armedAt = System.currentTimeMillis() - OPEN_SETTLE_MS - OPEN_REPOSITION_MS - 1
            lastPagerEvent = armedAt
            Log.d(TAG, "Player still visible after BACK; blocker re-armed")
        } else {
            reset()
            Log.d(TAG, "Reels player closed")
        }
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
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName?.toString() != INSTAGRAM) return
        if (getSharedPreferences("noreel", MODE_PRIVATE).getLong("until", 0L) <= System.currentTimeMillis()) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> syncTree(true)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val now = System.currentTimeMillis()
                if (now - lastTreeScan >= TREE_SCAN_MS) syncTree(false)
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> handleScroll(event)
        }
    }

    private fun handleScroll(event: AccessibilityEvent) {
        val source = event.source ?: return
        try {
            if (source.viewIdResourceName != CLIPS_PAGER_ID) {
                if (active) syncTree(false)
                return
            }
            val now = System.currentTimeMillis()
            if (now - lastBack < BACK_VERIFY_MS) return
            val destination = if (event.toIndex >= 0) event.toIndex else maxOf(event.fromIndex, event.toIndex)
            if (destination < 0) return

            if (!active) {
                active = true
                page = -1
                armedAt = now
                lastPagerEvent = 0L
                Log.d(TAG, "Reels pager detected")
            }

            if (page < 0) {
                if (now - armedAt <= OPEN_SETTLE_MS || event.fromIndex == event.toIndex) {
                    page = destination
                    lastPagerEvent = now
                    return
                }
                block(destination, now)
                return
            }

            if (destination == page) return

            // Instagram may reposition the shared pager immediately after opening a Reel
            // from a DM, profile or feed. Absorb only that brief initial reposition.
            if (now - armedAt <= OPEN_REPOSITION_MS ||
                (lastPagerEvent > 0L && now - lastPagerEvent <= OPEN_REPOSITION_MS)) {
                page = destination
                lastPagerEvent = now
                return
            }
            block(destination, now)
        } finally {
            source.recycle()
        }
    }

    private fun block(destination: Int, now: Long) {
        pendingPage = destination
        lastBack = now
        active = false
        handler.removeCallbacks(verifyBack)
        val success = performGlobalAction(GLOBAL_ACTION_BACK)
        Log.d(TAG, "Blocked Reel swipe; BACK dispatched=$success")
        handler.postDelayed(verifyBack, BACK_VERIFY_MS)
    }

    private fun syncTree(force: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastBack < BACK_VERIFY_MS) return
        if (!force && now - lastTreeScan < TREE_SCAN_MS) return
        lastTreeScan = now
        val found = findFullscreenPager()
        if (found) {
            if (!active) {
                active = true
                page = -1
                armedAt = now
                lastPagerEvent = 0L
                Log.d(TAG, "Full-screen Reels pager armed")
            }
        } else if (active && now - armedAt > 1_000L) {
            reset()
        }
    }

    private fun findFullscreenPager(): Boolean {
        val root = rootInActiveWindow ?: return active
        return try { containsPager(root, 0) } finally { root.recycle() }
    }

    private fun containsPager(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (depth > MAX_DEPTH) return false
        val id = node.viewIdResourceName.orEmpty()
        if (id == CLIPS_PAGER_ID && node.isVisibleToUser && fillsScreen(node)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = try { containsPager(child, depth + 1) } finally { child.recycle() }
            if (match) return true
        }
        return false
    }

    private fun fillsScreen(node: AccessibilityNodeInfo): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() <= 0 || rect.height() <= 0) return false
        val metrics = resources.displayMetrics
        val screen = metrics.widthPixels.toDouble() * metrics.heightPixels.toDouble()
        val area = rect.width().toDouble() * rect.height().toDouble()
        return screen > 0.0 && area / screen >= FULLSCREEN_RATIO
    }

    private fun reset() {
        active = false
        page = -1
        armedAt = 0L
        lastPagerEvent = 0L
    }

    override fun onInterrupt() {}
    override fun onDestroy() {
        handler.removeCallbacks(verifyBack)
        super.onDestroy()
    }
}
