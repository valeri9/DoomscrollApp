package com.valeri.doomscroll.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.valeri.doomscroll.classifier.RuleEngine
import com.valeri.doomscroll.classifier.ScreenClass
import com.valeri.doomscroll.data.Settings
import com.valeri.doomscroll.data.repo.DoomscrollRepository
import com.valeri.doomscroll.data.repo.toDomain
import com.valeri.doomscroll.learn.LearnMode
import com.valeri.doomscroll.learn.NodeInspector
import com.valeri.doomscroll.overlay.InterventionSpec
import com.valeri.doomscroll.overlay.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Strictly event-driven. No polling, no timers, no wake-ups: the framework hands us events
 * only for the packages listed in serviceInfo.packageNames, and everything else is derived
 * from those events.
 */
class DoomscrollAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ruleEngine = RuleEngine()
    private val sessions = SessionTracker()
    private lateinit var overlay: OverlayController
    private lateinit var repo: DoomscrollRepository

    @Volatile private var settings: Settings = Settings()

    /** Cached classification, keyed by the package it was computed for. */
    private var cachedPackage: String? = null
    private var cachedResult: RuleEngine.Classification? = null
    private var cachedAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = OverlayController(this)
        repo = DoomscrollRepository.get(this)

        scope.launch {
            repo.seedIfEmpty()
            ruleEngine.updateRules(repo.activeRules())
            applyMonitoredPackages(repo.monitoredPackagesNow())
        }
        scope.launch {
            repo.allRules.collectLatest { rules ->
                ruleEngine.updateRules(rules.filter { it.enabled }.map { it.toDomain() })
            }
        }
        scope.launch {
            repo.enabledPackages.collectLatest { applyMonitoredPackages(it) }
        }
        scope.launch {
            repo.settings.collectLatest { updated ->
                settings = updated
                sessions.cooldownMs = updated.cooldownMs
                sessions.minScrollEvents = updated.minScrollEvents
                sessions.minDwellMs = updated.minDwellMs
                sessions.reArmAfterMs = updated.reArmAfterMs
            }
        }
        Log.i(Tag.SERVICE, "connected")
    }

    /**
     * Narrows event delivery to just these packages. The framework does the filtering, so
     * unmonitored apps cost us literally nothing.
     */
    private fun applyMonitoredPackages(packages: Set<String>) {
        if (packages.isEmpty()) {
            Log.w(Tag.SERVICE, "no monitored apps; service is idle")
        }
        // serviceInfo is nullable before the framework has finished connecting. Leaving
        // packageNames unset means "every app" per the AccessibilityServiceInfo contract —
        // the exact opposite of the narrowing this exists for — so a silent no-op here would
        // quietly defeat the main battery optimization. Never claim success without checking.
        val info = serviceInfo
        if (info == null) {
            Log.w(Tag.SERVICE, "serviceInfo unavailable; could not narrow to $packages")
            return
        }
        info.packageNames = packages.toTypedArray()
        serviceInfo = info
        Log.i(Tag.SERVICE, "monitoring $packages")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!settings.enabled) return
        val packageName = event.packageName?.toString() ?: return

        // Every event counts as "still here". Re-arming is driven by the absence of these.
        sessions.noteActivity(packageName)

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                invalidateCache()
                maybeCapture(packageName, event)
                // Warm the cache so the first scroll doesn't pay for classification.
                classify(packageName, event.className)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                maybeCapture(packageName, event)
                // Usually a cache hit; only recomputes once the throttle window lapses.
                classify(packageName, event.className)
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (overlay.isShowing) return
                val result = classify(packageName, event.className)
                if (result.screenClass != ScreenClass.DOOMSCROLL) return
                if (!sessions.onDoomscrollScroll(packageName)) return

                Log.i(Tag.SERVICE, "TRIGGER $packageName (${result.describe})")
                scope.launch { intervene(packageName, result.matchedRule?.pattern ?: "unknown") }
            }
        }
    }

    private suspend fun intervene(packageName: String, contextLabel: String) {
        val current = settings
        val isNight = current.isNight()

        // Each reopen inside the same night window costs an extra escalation step.
        val breathingSeconds = if (isNight) {
            val priorTonight = repo.nightInterventionsSince(current.nightWindowStartMillis())
            current.nightBreathingSeconds + priorTonight * current.nightEscalationSeconds
        } else {
            current.dayBreathingSeconds
        }

        val interventionId = repo.startIntervention(
            packageName = packageName,
            contextLabel = contextLabel,
            isNightMode = isNight,
            breathingSeconds = breathingSeconds,
        )

        val spec = InterventionSpec(
            packageName = packageName,
            contextLabel = contextLabel,
            breathingSeconds = breathingSeconds,
            isNight = isNight,
            reasons = repo.reasonsFor(packageName),
            requireTypedReason = isNight,
            minReasonChars = current.nightMinReasonChars,
            continueDelaySeconds = if (isNight) current.nightContinueDelaySeconds else 0,
        )

        overlay.show(spec) { result ->
            scope.launch {
                repo.completeIntervention(
                    id = interventionId,
                    label = result.reasonLabel,
                    text = result.reasonText,
                    continued = result.continuedAnyway,
                )
            }
            // "Close the app" only means something if we actually leave it.
            if (!result.continuedAnyway) performGlobalAction(GLOBAL_ACTION_HOME)
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
        cachedPackage = packageName
        cachedResult = result
        cachedAt = now

        Log.d(Tag.CLASSIFIER, "$packageName [$className] -> ${result.describe} | ${sessions.debugState(packageName)}")
        return result
    }

    private fun invalidateCache() {
        cachedResult = null
        cachedAt = 0L
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
        scope.cancel()
        Log.i(Tag.SERVICE, "unbound")
        return super.onUnbind(intent)
    }
}
