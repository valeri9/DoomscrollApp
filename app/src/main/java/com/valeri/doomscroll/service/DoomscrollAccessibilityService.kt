package com.valeri.doomscroll.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.valeri.doomscroll.classifier.RuleEngine
import com.valeri.doomscroll.classifier.ScreenClass
import com.valeri.doomscroll.data.Settings
import com.valeri.doomscroll.data.repo.DoomscrollRepository
import com.valeri.doomscroll.data.repo.DoomscrollRepository.Companion.CONTEXT_REOPENED
import com.valeri.doomscroll.data.repo.DoomscrollRepository.Companion.CONTEXT_SWITCHED
import com.valeri.doomscroll.data.repo.toDomain
import com.valeri.doomscroll.learn.LearnMode
import com.valeri.doomscroll.learn.NodeInspector
import com.valeri.doomscroll.overlay.InterventionForegroundService
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
    private val doomscrollTimer = DoomscrollTimer()

    /** The app "Close the app" was last tapped in; tells a reopen apart from a hop. Main thread only. */
    private var lastClosedPackage: String? = null
    private lateinit var overlay: OverlayController
    private lateinit var repo: DoomscrollRepository

    @Volatile private var settings: Settings = Settings()

    /**
     * Cached classification, keyed by the package AND window class it was computed for.
     * Package alone is not enough: a same-package navigation (e.g. feed -> DM thread) that
     * fires only TYPE_WINDOW_CONTENT_CHANGED, not TYPE_WINDOW_STATE_CHANGED, never calls
     * invalidateCache(). Without the class-name key, a scroll on the new (legit) screen
     * within the throttle window would hit the stale feed classification and trigger.
     */
    private var cachedPackage: String? = null
    private var cachedClassName: String? = null
    private var cachedResult: RuleEngine.Classification? = null
    private var cachedAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = OverlayController(this)
        repo = DoomscrollRepository.get(this)
        // Pre-create the notification channel InterventionForegroundService needs, so its
        // first-ever activation doesn't pay that one-time setup cost on the same frame as
        // the first real overlay.
        InterventionForegroundService.ensureChannel(this)

        scope.launch {
            // seedIfEmpty() must complete before allRules is ever subscribed to. Starting
            // both concurrently raced Room's Flow against the seed insert: if the Flow's
            // first query ran before the seed committed, it emitted an empty list, and if
            // that empty emission's updateRules() landed after this coroutine's own
            // post-seed updateRules() call, every rule was silently wiped until the next
            // unrelated table write. Awaiting the seed first, then subscribing, means the
            // Flow's very first emission already reflects the seeded rows — no explicit
            // updateRules() call is needed here at all, so the two writers collapse into one.
            repo.seedIfEmpty()
            applyMonitoredPackages(repo.monitoredPackagesNow())
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
                // Also warms the cache so the first scroll doesn't pay for classification.
                countDoomscrollTime(packageName, classify(packageName, event.className))

                // Opening any watched app right after tapping "Close the app" is the urge moving
                // somewhere else, not a fresh visit — put the breath straight back up rather
                // than waiting for scroll/dwell thresholds to earn it again.
                if (!overlay.isShowing && sessions.consumeForceReopen(packageName)) {
                    val label = if (packageName == lastClosedPackage) CONTEXT_REOPENED else CONTEXT_SWITCHED
                    Log.i(Tag.SERVICE, "FORCE-REOPEN $packageName ($label, closed $lastClosedPackage)")
                    startIntervention(packageName, label)
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                maybeCapture(packageName, event)
                // Usually a cache hit; only recomputes once the throttle window lapses.
                countDoomscrollTime(packageName, classify(packageName, event.className))
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (overlay.isShowing) return
                val result = classify(packageName, event.className)
                countDoomscrollTime(packageName, result)
                if (result.screenClass != ScreenClass.DOOMSCROLL) return
                if (!sessions.onDoomscrollScroll(packageName)) return

                Log.i(Tag.SERVICE, "TRIGGER $packageName (${result.describe})")
                startIntervention(packageName, result.matchedRule?.pattern ?: "unknown")
            }
        }
    }

    /** Stops the doomscroll clock on this thread, where it lives, before handing off. */
    private fun startIntervention(packageName: String, contextLabel: String) {
        doomscrollTimer.stop()
        flushDoomscrollTime()
        scope.launch { intervene(packageName, contextLabel) }
    }

    private fun countDoomscrollTime(packageName: String, result: RuleEngine.Classification) {
        val onDoomscroll = result.screenClass == ScreenClass.DOOMSCROLL
        doomscrollTimer.onEvent(packageName, onDoomscroll)
        val pending = doomscrollTimer.pendingMs
        if (pending >= DetectionConfig.DOOMSCROLL_FLUSH_MS || (!onDoomscroll && pending > 0L)) {
            flushDoomscrollTime()
        }
    }

    // Anything still pending when the service is torn down (under DOOMSCROLL_FLUSH_MS) is lost;
    // not worth a write that outlives the scope for seconds of stats.
    private fun flushDoomscrollTime() {
        val batch = doomscrollTimer.drain()
        if (batch.isEmpty()) return
        scope.launch {
            batch.forEach { (key, ms) -> repo.addDoomscrollTime(key.first, key.second, ms) }
        }
    }

    private suspend fun intervene(packageName: String, contextLabel: String) {
        val current = settings
        val isNight = current.isNight()

        // The overlay draws an opaque screen on top, but the app underneath is not paused by
        // that alone — its video (and audio) keeps running, invisibly, behind it. Sending it
        // home for real, right now, triggers the same onPause/onStop most video players
        // already use to pause themselves on backgrounding, so playback actually stops rather
        // than merely being hidden. The task stays alive; resumeApp() below brings it back to
        // this exact same screen if the user continues.
        performGlobalAction(GLOBAL_ACTION_HOME)

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
            if (result.continuedAnyway) {
                // Already home from the top of this function; bring the same task back to
                // the front so playback resumes right where it paused, instead of the app
                // restarting from its own launch screen.
                resumeApp(packageName)
            } else {
                // Already home, and staying there is the whole point of "Close the app". Mark
                // it so opening this app or any other watched one inside the window puts the
                // prompt straight back up.
                lastClosedPackage = packageName
                sessions.noteClosedByIntervention()
            }
        }
    }

    private fun resumeApp(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            Log.w(Tag.SERVICE, "no launch intent for $packageName; cannot resume it")
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        runCatching { startActivity(launchIntent) }
            .onFailure { Log.w(Tag.SERVICE, "could not resume $packageName: ${it.message}") }
    }

    private fun classify(packageName: String, className: CharSequence?): RuleEngine.Classification {
        val now = System.currentTimeMillis()
        val classNameStr = className?.toString()
        val cached = cachedResult
        val fresh = now - cachedAt < DetectionConfig.CLASSIFY_THROTTLE_MS
        if (cached != null && cachedPackage == packageName && cachedClassName == classNameStr && fresh) {
            return cached
        }

        val root: AccessibilityNodeInfo? = try {
            rootInActiveWindow
        } catch (e: Exception) {
            Log.v(Tag.CLASSIFIER, "no root window: ${e.message}")
            null
        }

        val result = ruleEngine.classify(packageName, className, root)
        // Nodes obtained via rootInActiveWindow are still pool-managed on API <33
        // (minSdk 26); release it now that classification is done with it.
        @Suppress("DEPRECATION")
        root?.recycle()
        cachedPackage = packageName
        cachedClassName = classNameStr
        cachedResult = result
        cachedAt = now

        Log.d(Tag.CLASSIFIER, "$packageName [$className] -> ${result.describe} | ${sessions.debugState(packageName)}")
        return result
    }

    private fun invalidateCache() {
        cachedResult = null
        cachedClassName = null
        cachedAt = 0L
    }

    /** Learn Mode: dump the hierarchy, but only while the UI has explicitly armed a capture. */
    private fun maybeCapture(packageName: String, event: AccessibilityEvent) {
        if (!LearnMode.isArmed) return
        // A separate rootInActiveWindow call from classify()'s, so it needs its own release —
        // same reasoning as there: the per-process node pool is real on minSdk 26-32.
        val root = rootInActiveWindow
        val nodes = NodeInspector.capture(root)
        @Suppress("DEPRECATION")
        root?.recycle()
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
        cleanup("unbound")
        return super.onUnbind(intent)
    }

    // Teardown was previously reachable only through onUnbind(). If the framework or an
    // OEM's background killer tears the process down via onDestroy() without a preceding
    // onUnbind() — some ROMs do this under battery restrictions for accessibility services —
    // scope.cancel() never ran and a currently-shown overlay was never explicitly hidden.
    // Cancelling an already-cancelled Job (and hiding an already-hidden overlay) is a no-op,
    // so it's safe for both paths to call the same cleanup.
    override fun onDestroy() {
        cleanup("destroyed")
        super.onDestroy()
    }

    private fun cleanup(reason: String) {
        if (::overlay.isInitialized) overlay.hide()
        sessions.reset()
        scope.cancel()
        Log.i(Tag.SERVICE, reason)
    }
}
