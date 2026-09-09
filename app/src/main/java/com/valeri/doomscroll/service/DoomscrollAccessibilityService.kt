package com.valeri.doomscroll.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.valeri.doomscroll.classifier.BuiltInRules
import com.valeri.doomscroll.classifier.RuleEngine
import com.valeri.doomscroll.classifier.ScreenClass
import com.valeri.doomscroll.learn.LearnMode
import com.valeri.doomscroll.learn.NodeInspector
import com.valeri.doomscroll.overlay.OverlayController

/**
 * Strictly event-driven. No polling, no timers, no wake-ups: the framework hands us events
 * only for the packages listed in serviceInfo.packageNames, and everything else is derived
 * from those events.
 */
class DoomscrollAccessibilityService : AccessibilityService() {

    private val ruleEngine = RuleEngine()
    private val sessions = SessionTracker()
    private lateinit var overlay: OverlayController

    /** Cached classification, keyed by the window it was computed for. */
    private var cachedWindowId: Int = -1
    private var cachedPackage: String? = null
    private var cachedResult: RuleEngine.Classification? = null
    private var cachedAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = OverlayController(this)
        applyMonitoredPackages(BuiltInRules.defaultMonitoredPackages)
        Log.i(Tag.SERVICE, "connected; monitoring ${BuiltInRules.defaultMonitoredPackages}")
    }

    /**
     * Narrows event delivery to just these packages. The framework does the filtering, so
     * unmonitored apps cost us literally nothing.
     */
    fun applyMonitoredPackages(packages: Set<String>) {
        serviceInfo = serviceInfo?.apply { packageNames = packages.toTypedArray() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                sessions.onAppForegrounded(packageName)
                invalidateCache()
                maybeCapture(packageName, event)
                // Warm the cache so the first scroll doesn't pay for classification.
                classify(packageName, event.className)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                maybeCapture(packageName, event)
                // Cheap: usually a cache hit. Only recomputes once the throttle window lapses.
                classify(packageName, event.className)
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (overlay.isShowing) return
                val result = classify(packageName, event.className)
                if (result.screenClass != ScreenClass.DOOMSCROLL) return

                if (sessions.onDoomscrollScroll(packageName)) {
                    Log.i(Tag.SERVICE, "TRIGGER $packageName (${result.describe})")
                    overlay.show(
                        packageName = packageName,
                        contextLabel = result.matchedRule?.pattern ?: "unknown",
                        breathingSeconds = DetectionConfig.BREATHING_SECONDS,
                    )
                }
            }
        }
    }

    private fun classify(packageName: String, className: CharSequence?): RuleEngine.Classification {
        val now = System.currentTimeMillis()
        val cached = cachedResult
        val fresh = now - cachedAt < DetectionConfig.CLASSIFY_THROTTLE_MS
        if (cached != null && cachedPackage == packageName && fresh) return cached

        val root: AccessibilityNodeInfo? = try {
            rootInActiveWindow
        } catch (e: Exception) {
            Log.v(Tag.CLASSIFIER, "no root window: ${e.message}")
            null
        }

        val result = ruleEngine.classify(packageName, className, root)

        cachedWindowId = root?.windowId ?: -1
        cachedPackage = packageName
        cachedResult = result
        cachedAt = now

        Log.d(Tag.CLASSIFIER, "$packageName [$className] -> ${result.describe} | ${sessions.debugState(packageName)}")
        return result
    }

    private fun invalidateCache() {
        cachedResult = null
        cachedAt = 0L
        cachedWindowId = -1
    }

    /** Learn Mode: dump the hierarchy, but only while the UI has explicitly armed a capture. */
    private fun maybeCapture(packageName: String, event: AccessibilityEvent) {
        if (!LearnMode.isArmed) return
        val nodes = NodeInspector.capture(rootInActiveWindow)
        if (nodes.isEmpty()) return
        LearnMode.record(
            LearnMode.Capture(
                packageName = packageName,
                windowClassName = event.className?.toString().orEmpty(),
                nodes = nodes,
            )
        )
        Log.i(Tag.SERVICE, "captured ${nodes.size} ids from $packageName")
        nodes.forEach { Log.i(Tag.SERVICE, "  id=${it.shortId} class=${it.className} scrollable=${it.scrollable}") }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (::overlay.isInitialized) overlay.hide()
        sessions.reset()
        Log.i(Tag.SERVICE, "unbound")
        return super.onUnbind(intent)
    }
}
