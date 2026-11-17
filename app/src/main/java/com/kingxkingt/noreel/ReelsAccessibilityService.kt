package com.kingxkingt.noreel

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Blocks Instagram's dedicated Reels tab and full-screen Reels viewer.
 *
 * Instagram calls Reels "clips" internally. We use the specific clips_tab and
 * clips_viewer_* IDs. We deliberately do not match generic reel_* IDs because
 * those can refer to Stories.
 */
class ReelsAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "NoReel"
        private const val INSTAGRAM = "com.instagram.android"
        private const val PREFS = "noreel"
        private const val SCAN_INTERVAL_MS = 180L
        private const val VERIFY_DELAY_MS = 450L
        private const val TAB_MISSING_GRACE_MS = 700L
        private const val MAX_BACK_ATTEMPTS = 3
        private const val MAX_DEPTH = 35
        private const val MAX_NODES = 1800

        private val REELS_TAB_IDS = setOf(
            "clips_tab",
            "reels_tab",
            "tab_clips",
            "reels_nav_item",
            "clips_nav_item"
        )
        private val REELS_VIEWER_IDS = setOf(
            "clips_viewer_view_pager",
            "clips_viewer_fragment_container",
            "clips_viewer_container",
            "clips_video_container",
            "clips_recycler_view"
        )
        private val INBOX_TAB_IDS = setOf(
            "direct_tab",
            "inbox_tab",
            "action_bar_inbox_button"
        )
        private val REELS_LABELS = setOf("reels", "reel", "reels tab", "reel tab", "watch reels")
    }

    private data class TabMatch(
        val bounds: Rect,
        val selected: Boolean
    )

    private val handler = Handler(Looper.getMainLooper())
    private var lastScanAt = 0L
    private var lastDiagnosticAt = 0L
    private var lastBackAt = 0L
    private var backAttempts = 0
    private var closingViewer = false
    private var windowManager: WindowManager? = null
    private var tabCover: View? = null
    private var tabCoverBounds: Rect? = null
    private var lastTabSeenAt = 0L
    private var tabExitAt = 0L

    private val verifyViewer = Runnable {
        if (!closingViewer) return@Runnable

        if (!isViewerOpen()) {
            closingViewer = false
            backAttempts = 0
            lastBackAt = 0L
            Log.d(TAG, "Reels viewer closed")
        } else if (backAttempts < MAX_BACK_ATTEMPTS) {
            sendBack("viewer verification")
        } else {
            closingViewer = false
            recordStatus("Reels viewer detected but Instagram did not close")
            Log.w(TAG, "Viewer still visible after retry limit")
        }
    }

    private val verifyTabExit = Runnable {
        if (!CommitmentTimer.isActive(this)) return@Runnable
        val root = rootInActiveWindow ?: return@Runnable
        try {
            if (root.packageName?.toString() == INSTAGRAM) {
                val tab = findReelsTab(root, 0, intArrayOf(0))
                if (tab?.selected == true) leaveReelsTab(root)
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
                AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_VIEW_SELECTED
            packageNames = arrayOf(INSTAGRAM)
            notificationTimeout = 60
            flags = flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }

        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean("service_connected", true)
            .putString("last_detection", "Waiting for Instagram")
            .apply()

        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        if (event.packageName?.toString() != INSTAGRAM) {
            removeTabCover()
            return
        }

        if (!CommitmentTimer.isActive(this)) {
            removeTabCover()
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                val source = event.source
                val isReelsTabEvent = if (source == null) false else try {
                    isReelsTabOrChild(source)
                } finally {
                    source.recycle()
                }

                if (isReelsTabEvent) {
                    handler.removeCallbacks(verifyTabExit)
                    handler.postDelayed(verifyTabExit, 220L)
                }
                scanSoon()
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> scanSoon()
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> scanSoon()
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val source = event.source
                val scrollIsInsideViewer = if (source == null) false else try {
                    isInsideReelsViewer(source)
                } finally {
                    source.recycle()
                }
                if (scrollIsInsideViewer) closeViewer() else scanSoon()
            }
        }
    }

    private fun scanSoon() {
        val now = System.currentTimeMillis()
        if (now - lastScanAt < SCAN_INTERVAL_MS) return
        lastScanAt = now
        handler.post { scanInstagramWindow() }
    }

    private fun scanInstagramWindow() {
        if (!CommitmentTimer.isActive(this)) {
            removeTabCover()
            return
        }

        val root = rootInActiveWindow ?: return
        try {
            if (root.packageName?.toString() != INSTAGRAM) return

            // The full-screen viewer is checked first so a viewer launched from a DM,
            // profile, feed, or Reels tab is closed without covering ordinary screens.
            if (containsReelsViewer(root, 0, intArrayOf(0))) {
                removeTabCover()
                if (!closingViewer) {
                    recordBlock("Full-screen Reels viewer")
                }
                closeViewer()
                return
            }

            val tab = findReelsTab(root, 0, intArrayOf(0))
            if (tab == null) {
                // Instagram occasionally publishes a partial accessibility tree while
                // rebuilding its bottom bar. Keep the replacement icon briefly so the
                // original Reels glyph does not flash back during that gap.
                val keepExistingCover = tabCover != null &&
                    System.currentTimeMillis() - lastTabSeenAt <= TAB_MISSING_GRACE_MS
                if (!keepExistingCover) {
                    removeTabCover()
                } else {
                    handler.postDelayed({ scanInstagramWindow() }, 200L)
                }
                recordNoMatchSparingly()
                return
            }

            lastTabSeenAt = System.currentTimeMillis()
            if (tab.selected) {
                removeTabCover()
                leaveReelsTab(root)
                return
            }

            if (tabCover == null || tabCoverBounds != tab.bounds) {
                showTabCover(tab.bounds)
            }
            recordStatus("Reels navigation tab replaced with NoReel stop icon")
        } finally {
            root.recycle()
        }
    }

    private fun shortId(node: AccessibilityNodeInfo): String =
        node.viewIdResourceName?.substringAfterLast('/')?.lowercase().orEmpty()

    private fun labels(node: AccessibilityNodeInfo): List<String> =
        listOfNotNull(
            node.contentDescription?.toString(),
            node.text?.toString(),
            node.viewIdResourceName?.substringAfterLast('/')
        ).map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    private fun hasReelsLabel(node: AccessibilityNodeInfo): Boolean =
        labels(node).any { it in REELS_LABELS }

    private fun isBottomNavigation(bounds: Rect): Boolean {
        val metrics = resources.displayMetrics
        val maxCellWidth = (metrics.widthPixels * 0.38f).toInt()
        return bounds.width() in 1..maxCellWidth &&
            bounds.height() in 1..dp(120) &&
            bounds.top >= metrics.heightPixels * 0.78f &&
            bounds.bottom <= metrics.heightPixels + dp(48)
    }

    private fun isReelsTabOrChild(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        var depth = 0
        try {
            while (current != null && depth <= 7) {
                if (current.isVisibleToUser) {
                    val id = shortId(current)
                    if (id in REELS_TAB_IDS) return true

                    val bounds = Rect()
                    current.getBoundsInScreen(bounds)
                    if (isBottomNavigation(bounds) && hasReelsLabel(current)) return true
                }

                val parent = current.parent
                current.recycle()
                current = parent
                depth++
            }
        } finally {
            current?.recycle()
        }
        return false
    }

    private fun findReelsTab(
        node: AccessibilityNodeInfo,
        depth: Int,
        visited: IntArray
    ): TabMatch? {
        if (depth > MAX_DEPTH || visited[0] >= MAX_NODES || !node.isVisibleToUser) return null
        visited[0]++

        val id = shortId(node)
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        // clips_tab is a specific Instagram navigation ID. Do not require a guessed
        // bottom-screen percentage for this exact marker; that was too restrictive.
        val exactId = id in REELS_TAB_IDS
        val labelFallback = isBottomNavigation(bounds) && hasReelsLabel(node)

        if (exactId || labelFallback) {
            val target = bestTabBounds(node, bounds)
            // Exact IDs are useful, but Instagram has exposed stale/incorrect bounds
            // for this node on some layouts. Never place an overlay outside the real
            // bottom-navigation strip (for example, over Stories or the feed).
            if (target != null && isBottomNavigation(target)) {
                val selected = node.isSelected || hasSelectedDescendant(node, 0, intArrayOf(0))
                return TabMatch(target, selected)
            }
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val match = try {
                findReelsTab(child, depth + 1, visited)
            } finally {
                child.recycle()
            }
            if (match != null) return match
        }
        return null
    }

    private fun hasSelectedDescendant(
        node: AccessibilityNodeInfo,
        depth: Int,
        visited: IntArray
    ): Boolean {
        if (depth > 4 || visited[0] > 100) return false
        visited[0]++
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val selected = try {
                child.isVisibleToUser &&
                    (child.isSelected || hasSelectedDescendant(child, depth + 1, visited))
            } finally {
                child.recycle()
            }
            if (selected) return true
        }
        return false
    }

    /**
     * Prefer the clickable bottom-navigation cell above the icon node. Some Instagram
     * layouts expose the icon and its clickable parent as separate accessibility nodes.
     */
    private fun bestTabBounds(node: AccessibilityNodeInfo, original: Rect): Rect? {
        val result = Rect(original)
        val metrics = resources.displayMetrics
        val maxCellWidth = metrics.widthPixels * 0.38f
        val maxCellHeight = dp(145)

        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        var depth = 0
        try {
            while (current != null && depth < 6) {
                val parent = current.parent
                current.recycle()
                current = parent
                if (current != null) {
                    val parentBounds = Rect()
                    current.getBoundsInScreen(parentBounds)
                    val looksLikeTabCell =
                        current.isVisibleToUser &&
                        current.isClickable &&
                        isBottomNavigation(parentBounds) &&
                        parentBounds.width() <= maxCellWidth &&
                        parentBounds.height() <= maxCellHeight

                    if (looksLikeTabCell) result.set(parentBounds)
                }
                depth++
            }
        } finally {
            current?.recycle()
        }

        if (result.width() <= 0 || result.height() <= 0) return null
        return result
    }

    /**
     * Instagram has used several dedicated "clips" containers for the full-screen
     * Reels player. Require a large on-screen node so a small inline DM preview
     * cannot be mistaken for the full-screen viewer.
     */
    private fun isReelsViewerMarker(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser || shortId(node) !in REELS_VIEWER_IDS) return false

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val metrics = resources.displayMetrics
        val screenArea = metrics.widthPixels.toDouble() * metrics.heightPixels.toDouble()
        if (screenArea <= 0.0) return false

        val coverage = bounds.width().toDouble() * bounds.height().toDouble() / screenArea
        return bounds.width() >= metrics.widthPixels * 0.55f &&
            bounds.height() >= metrics.heightPixels * 0.45f &&
            coverage >= 0.40
    }

    private fun isInsideReelsViewer(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        var depth = 0
        try {
            while (current != null && depth <= 8) {
                if (isReelsViewerMarker(current)) return true

                val parent = current.parent
                current.recycle()
                current = parent
                depth++
            }
        } finally {
            current?.recycle()
        }
        return false
    }

    private fun containsReelsViewer(
        node: AccessibilityNodeInfo,
        depth: Int,
        visited: IntArray
    ): Boolean {
        if (depth > MAX_DEPTH || visited[0] >= MAX_NODES || !node.isVisibleToUser) return false
        visited[0]++

        // Never match generic reel_* IDs: Instagram also uses those for Stories.
        if (isReelsViewerMarker(node)) return true

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val found = try {
                containsReelsViewer(child, depth + 1, visited)
            } finally {
                child.recycle()
            }
            if (found) return true
        }
        return false
    }

    private fun isViewerOpen(): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            root.packageName?.toString() == INSTAGRAM &&
                containsReelsViewer(root, 0, intArrayOf(0))
        } finally {
            root.recycle()
        }
    }

    private fun closeViewer() {
        if (closingViewer && System.currentTimeMillis() - lastBackAt < VERIFY_DELAY_MS) return
        closingViewer = true
        sendBack("Reels viewer detected")
    }

    private fun sendBack(reason: String) {
        lastBackAt = System.currentTimeMillis()
        backAttempts++
        val sent = performGlobalAction(GLOBAL_ACTION_BACK)
        Log.i(TAG, "Back from Reels ($reason), attempt=$backAttempts, sent=$sent")

        handler.removeCallbacks(verifyViewer)
        handler.postDelayed(verifyViewer, VERIFY_DELAY_MS)
    }

    private fun leaveReelsTab(root: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        if (now - tabExitAt < 900L) return
        tabExitAt = now

        // Prefer the Instagram inbox tab so the user lands somewhere useful. If it
        // is not exposed in the current hierarchy, fall back to Android Back.
        val moved = clickTargetById(root, INBOX_TAB_IDS, 0, intArrayOf(0))
        if (!moved) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        Log.i(TAG, "Redirected away from selected Reels tab")
    }

    private fun clickTargetById(
        node: AccessibilityNodeInfo,
        targetIds: Set<String>,
        depth: Int,
        visited: IntArray
    ): Boolean {
        if (depth > MAX_DEPTH || visited[0] >= MAX_NODES || !node.isVisibleToUser) return false
        visited[0]++

        if (shortId(node) in targetIds) {
            var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
            var ancestorDepth = 0
            try {
                while (current != null && ancestorDepth <= 5) {
                    if (current.isClickable &&
                        current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    ) return true

                    val parent = current.parent
                    current.recycle()
                    current = parent
                    ancestorDepth++
                }
            } finally {
                current?.recycle()
            }
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val clicked = try {
                clickTargetById(child, targetIds, depth + 1, visited)
            } finally {
                child.recycle()
            }
            if (clicked) return true
        }
        return false
    }

    private fun showTabCover(bounds: Rect) {
        removeTabCover()

        val metrics = resources.displayMetrics
        val left = bounds.left.coerceAtLeast(0)
        val top = bounds.top.coerceAtLeast(0)
        val right = bounds.right.coerceAtMost(metrics.widthPixels)
        val bottom = bounds.bottom.coerceAtMost(metrics.heightPixels)
        if (right <= left || bottom <= top) return

        val darkMode = (resources.configuration.uiMode and 0x30) == 0x20
        val overlay = object : View(this) {
            private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = if (darkMode) Color.BLACK else Color.WHITE
            }
            private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(2).toFloat()
                strokeCap = Paint.Cap.ROUND
                color = if (darkMode) Color.WHITE else Color.rgb(38, 38, 38)
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                // Draw only a compact circle-slash glyph. The rest of the tab cell
                // stays transparent, so the Instagram bar keeps its native spacing.
                val radius = minOf(dp(14).toFloat(), minOf(width, height) * 0.47f)
                if (radius < dp(6)) return
                val cx = width / 2f
                val cy = height / 2f
                canvas.drawCircle(cx, cy, radius + dp(1).toFloat(), fill)
                canvas.drawCircle(cx, cy, radius - dp(1).toFloat(), outline)
                canvas.drawLine(
                    cx - radius * 0.56f,
                    cy + radius * 0.56f,
                    cx + radius * 0.56f,
                    cy - radius * 0.56f,
                    outline
                )
            }
        }.apply {
            isClickable = true
            isFocusable = false
            contentDescription = "Reels blocked by NoReel"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }

        val layout = WindowManager.LayoutParams(
            right - left,
            bottom - top,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = left
            y = top
        }

        try {
            windowManager?.addView(overlay, layout)
            tabCover = overlay
            tabCoverBounds = Rect(bounds)
            recordBlock("Reels navigation tab replaced with NoReel stop icon")
            Log.i(TAG, "Reels navigation stop icon placed at " + bounds.toShortString())
        } catch (error: Exception) {
            Log.w(TAG, "Could not replace Reels tab icon", error)
            recordStatus("Reels tab found, but the stop icon could not be placed")
        }
    }

    private fun removeTabCover() {
        val overlay = tabCover
        if (overlay != null) {
            runCatching { windowManager?.removeView(overlay) }
        }
        tabCover = null
        tabCoverBounds = null
        lastTabSeenAt = 0L
    }

    private fun recordBlock(description: String) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val count = prefs.getInt("block_count", 0) + 1
        prefs.edit()
            .putInt("block_count", count)
            .putString("last_detection", description)
            .putLong("last_detection_at", System.currentTimeMillis())
            .apply()
    }

    private fun recordStatus(description: String) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString("last_detection", description)
            .putLong("last_detection_at", System.currentTimeMillis())
            .apply()
    }

    private fun recordNoMatchSparingly() {
        val now = System.currentTimeMillis()
        if (now - lastDiagnosticAt < 2500L) return
        lastDiagnosticAt = now
        recordStatus("Instagram checked; no Reels marker matched on this screen")
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onInterrupt() {
        removeTabCover()
    }

    override fun onDestroy() {
        handler.removeCallbacks(verifyViewer)
        handler.removeCallbacks(verifyTabExit)
        removeTabCover()
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean("service_connected", false)
            .apply()
        super.onDestroy()
    }
}
