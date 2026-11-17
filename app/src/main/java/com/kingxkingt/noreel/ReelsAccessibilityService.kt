package com.kingxkingt.noreel

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Color
import android.graphics.Rect
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ReelsAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "NoReel"
        private const val INSTAGRAM = "com.instagram.android"
        private const val SCAN_MS = 300L
        private const val RETRY_MS = 500L
        private const val COOLDOWN_MS = 1200L
        private const val MAX_TRIES = 4
        private const val MAX_DEPTH = 30

        // Instagram calls Reels "clips" internally. Avoid generic reel_* IDs because
        // Instagram also uses those for Stories.
        private val VIEWER_ID_HINTS = listOf(
            "clips_viewer_view_pager",
            "clips_viewer_container",
            "clips_video_container",
            "clips_recycler_view"
        )
        private val TAB_ID_HINTS = listOf(
            "clips_tab",
            "reels_tab",
            "tab_clips",
            "reels_nav_item",
            "clips_nav_item"
        )
        private val REELS_LABELS = listOf("reels", "reel", "reels tab", "reel tab", "watch reels")
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastScan = 0L
    private var lastBack = 0L
    private var tries = 0
    private var closing = false
    private var windowManager: WindowManager? = null
    private var cover: View? = null
    private var coverBounds: Rect? = null

    private val verify = Runnable {
        if (!closing) return@Runnable
        if (!hasViewer()) {
            closing = false
            tries = 0
            lastBack = 0L
        } else if (tries < MAX_TRIES) {
            sendBack("verification retry")
        } else {
            Log.w(TAG, "Reels viewer remained after retry limit")
            closing = false
        }
    }

    private val reelsClickFallback = Runnable {
        if (!CommitmentTimer.isActive(this)) return@Runnable
        val root = rootInActiveWindow ?: return@Runnable
        try {
            if (hasViewer(root)) {
                removeCover()
                closeViewer("Reels tab opened")
            } else {
                Log.d(TAG, "Reels tab click detected; returning to previous Instagram screen")
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        } finally {
            root.recycle()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED or
                AccessibilityEvent.TYPE_VIEW_CLICKED
            packageNames = arrayOf(INSTAGRAM)
            notificationTimeout = 80
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName?.toString() != INSTAGRAM) {
            removeCover()
            return
        }
        if (!CommitmentTimer.isActive(this)) {
            removeCover()
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val source = event.source
                if (source != null) {
                    val clickedReels = try {
                        isReelsTabNode(source)
                    } finally {
                        source.recycle()
                    }
                    if (clickedReels) {
                        handler.removeCallbacks(reelsClickFallback)
                        handler.postDelayed(reelsClickFallback, 180L)
                        return
                    }
                }
                scanIfNeeded()
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val source = event.source
                val pagerScroll = if (source == null) false else try {
                    isInsideViewer(source)
                } finally {
                    source.recycle()
                }
                if (pagerScroll) closeViewer("Reels pager scrolled") else scanIfNeeded()
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> scan()

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> scanIfNeeded()
        }
    }

    private fun scanIfNeeded() {
        if (System.currentTimeMillis() - lastScan >= SCAN_MS) scan()
    }

    private fun scan() {
        lastScan = System.currentTimeMillis()
        val root = rootInActiveWindow ?: return
        try {
            if (hasViewer(root)) {
                removeCover()
                closeViewer("full-screen Reels player detected")
                return
            }

            val reelsTab = findReelsTab(root, 0)
            if (reelsTab == null) {
                removeCover()
            } else if (reelsTab != coverBounds) {
                showCover(reelsTab)
            }
        } finally {
            root.recycle()
        }
    }

    private fun viewId(node: AccessibilityNodeInfo): String =
        (node.viewIdResourceName ?: "").lowercase()

    private fun nodeLabels(node: AccessibilityNodeInfo): List<String> =
        listOfNotNull(
            node.text?.toString(),
            node.contentDescription?.toString(),
            node.viewIdResourceName?.substringAfterLast('/')
        ).map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    private fun matchesAnyId(node: AccessibilityNodeInfo, hints: List<String>): Boolean {
        val id = viewId(node)
        return id.isNotEmpty() && hints.any { id.endsWith(it) || id.contains("/" + it) }
    }

    private fun isViewerNode(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser || !matchesAnyId(node, VIEWER_ID_HINTS)) return false
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val metrics = resources.displayMetrics
        val screenArea = metrics.widthPixels.toDouble() * metrics.heightPixels.toDouble()
        if (screenArea <= 0.0) return false
        val coverage = bounds.width().toDouble() * bounds.height().toDouble() / screenArea
        return bounds.width() >= metrics.widthPixels * 0.70 &&
            bounds.height() >= metrics.heightPixels * 0.55 &&
            coverage >= 0.60
    }

    private fun hasViewer(root: AccessibilityNodeInfo): Boolean = findViewer(root, 0)

    private fun findViewer(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (depth > MAX_DEPTH) return false
        if (isViewerNode(node)) return true
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val found = try {
                findViewer(child, depth + 1)
            } finally {
                child.recycle()
            }
            if (found) return true
        }
        return false
    }

    private fun hasViewer(): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            hasViewer(root)
        } finally {
            root.recycle()
        }
    }

    private fun isInsideViewer(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        var level = 0
        try {
            while (current != null && level <= 6) {
                if (isViewerNode(current)) return true
                val parent = current.parent
                current.recycle()
                current = parent
                level++
            }
        } finally {
            current?.recycle()
        }
        return false
    }

    private fun isBottomNavigation(bounds: Rect): Boolean {
        val height = resources.displayMetrics.heightPixels
        return bounds.width() > 0 && bounds.height() > 0 &&
            bounds.top >= height * 0.70f && bounds.bottom <= height + dp(12)
    }

    private fun hasClickableAncestor(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        var level = 0
        try {
            while (current != null && level <= 4) {
                if (current.isClickable || current.isFocusable) return true
                val parent = current.parent
                current.recycle()
                current = parent
                level++
            }
        } finally {
            current?.recycle()
        }
        return false
    }

    private fun isReelsTabNode(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser) return false
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (!isBottomNavigation(bounds)) return false

        if (matchesAnyId(node, TAB_ID_HINTS)) return true
        val labels = nodeLabels(node)
        val labelMatch = labels.any { label ->
            REELS_LABELS.any { hint -> label == hint || label.startsWith(hint + " ") }
        }
        return labelMatch && hasClickableAncestor(node)
    }

    private fun findReelsTab(node: AccessibilityNodeInfo, depth: Int): Rect? {
        if (depth > MAX_DEPTH) return null
        if (isReelsTabNode(node)) return navItemBounds(node)

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val found = try {
                findReelsTab(child, depth + 1)
            } finally {
                child.recycle()
            }
            if (found != null) return found
        }
        return null
    }

    private fun navItemBounds(node: AccessibilityNodeInfo): Rect {
        val result = Rect()
        node.getBoundsInScreen(result)
        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        var level = 0
        try {
            while (current != null && level < 5) {
                val parent = current.parent
                current.recycle()
                current = parent
                if (current != null) {
                    val parentBounds = Rect()
                    current.getBoundsInScreen(parentBounds)
                    val maxWidth = resources.displayMetrics.widthPixels * 0.34f
                    val maxHeight = resources.displayMetrics.heightPixels * 0.24f
                    if (current.isVisibleToUser && current.isClickable &&
                        isBottomNavigation(parentBounds) &&
                        parentBounds.width() <= maxWidth &&
                        parentBounds.height() <= maxHeight
                    ) {
                        result.set(parentBounds)
                    }
                }
                level++
            }
        } finally {
            current?.recycle()
        }
        return result
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun showCover(bounds: Rect) {
        removeCover()
        val padding = dp(4)
        val metrics = resources.displayMetrics
        val left = (bounds.left - padding).coerceAtLeast(0)
        val top = (bounds.top - padding).coerceAtLeast(0)
        val right = (bounds.right + padding).coerceAtMost(metrics.widthPixels)
        val bottom = (bounds.bottom + padding).coerceAtMost(metrics.heightPixels)
        if (right <= left || bottom <= top) return

        val params = WindowManager.LayoutParams(
            right - left,
            bottom - top,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = left
            y = top
        }
        val nightMode = (resources.configuration.uiMode and 0x30) == 0x20
        val overlay = View(this).apply {
            setBackgroundColor(if (nightMode) Color.BLACK else Color.WHITE)
            isClickable = true
            isFocusable = false
            contentDescription = "Reels tab blocked by NoReel"
        }

        try {
            windowManager?.addView(overlay, params)
            cover = overlay
            coverBounds = Rect(bounds)
            Log.d(TAG, "Covered Instagram Reels tab at " + bounds.toShortString())
        } catch (error: Exception) {
            Log.w(TAG, "Could not cover Instagram Reels tab", error)
        }
    }

    private fun removeCover() {
        val current = cover ?: return
        runCatching { windowManager?.removeView(current) }
        cover = null
        coverBounds = null
    }

    private fun closeViewer(reason: String) {
        if (System.currentTimeMillis() - lastBack < COOLDOWN_MS) return
        if (!closing) {
            closing = true
            tries = 0
        }
        sendBack(reason)
    }

    private fun sendBack(reason: String) {
        lastBack = System.currentTimeMillis()
        tries++
        val sent = performGlobalAction(GLOBAL_ACTION_BACK)
        Log.d(TAG, "Back action (" + reason + "), attempt=" + tries + ", sent=" + sent)
        handler.removeCallbacks(verify)
        handler.postDelayed(verify, RETRY_MS)
    }

    override fun onInterrupt() {
        removeCover()
    }

    override fun onDestroy() {
        handler.removeCallbacks(verify)
        handler.removeCallbacks(reelsClickFallback)
        removeCover()
        super.onDestroy()
    }
}
