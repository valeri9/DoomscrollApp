package com.valeri.doomscroll.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.valeri.doomscroll.service.Tag

/**
 * Owns the single intervention window. The accessibility service is already a persistent
 * system-bound process, so it can host the overlay directly — no foreground service needed.
 */
class OverlayController(private val service: AccessibilityService) {

    private val windowManager =
        service.getSystemService(AccessibilityService.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var container: BlockingFrameLayout? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    /** Last-resort dismissal so a bug can never leave the phone permanently covered. */
    private val safetyDismiss = Runnable {
        Log.w(Tag.OVERLAY, "safety timeout reached; dismissing")
        hide()
    }

    var isShowing: Boolean = false
        private set

    /**
     * The check-and-set for isShowing lives entirely inside this posted block, not before
     * it. intervene() runs on Dispatchers.Default, a real thread pool, and different
     * monitored apps keep independent session state — so two triggers close together
     * (e.g. a backgrounded app's lingering events overlapping a foreground trigger) could
     * call show() from two different threads at nearly the same moment. A check-then-set
     * split across "read isShowing here, write it after a post" is not atomic across
     * threads: both calls could see isShowing == false before either's write becomes
     * visible, both would post to the main thread, and the second showInternal() would
     * silently overwrite container/lifecycleOwner — leaking the first overlay's
     * WindowManager view with nothing left pointing at it to remove it. That leaked window
     * is still focusable and still swallows BACK, so the failure mode is not a cosmetic
     * glitch — it is the phone's back button quietly stopping working until the service
     * happens to rebind.
     *
     * Posting the check itself, rather than just the work that follows it, means every
     * call is serialized through the single main-thread queue: whichever call's Runnable
     * runs first sees the true state and wins, and everything after it correctly sees
     * isShowing == true and backs off. No @Volatile or lock can fix a race that spans two
     * separate statements on two different threads; moving both statements onto one
     * thread removes the race instead of narrowing it.
     */
    fun show(spec: InterventionSpec, onComplete: (InterventionResult) -> Unit) {
        if (!Settings.canDrawOverlays(service)) {
            Log.w(Tag.OVERLAY, "SYSTEM_ALERT_WINDOW not granted; cannot show intervention")
            return
        }
        mainHandler.post {
            if (isShowing) {
                Log.w(Tag.OVERLAY, "already showing; dropping trigger for ${spec.packageName}")
                return@post
            }
            isShowing = true
            showInternal(spec, onComplete)
        }
    }

    private fun showInternal(spec: InterventionSpec, onComplete: (InterventionResult) -> Unit) {
        val owner = OverlayLifecycleOwner().apply { onCreate() }

        // The owners must sit on the view that is added to the WindowManager, not on the
        // ComposeView inside it: Compose resolves them by walking UP from the window's root,
        // so setting them on the child alone throws "ViewTreeLifecycleOwner not found" the
        // moment the view attaches.
        val view = BlockingFrameLayout(service).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
        }

        val compose = ComposeView(service).apply {
            setContent {
                InterventionScreen(spec = spec) { result ->
                    onComplete(result)
                    hide()
                }
            }
        }
        view.addView(compose)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Focusable on purpose: it makes the window modal, lets us intercept BACK, and is
            // a prerequisite for the IME reaching the typed-reason field at night. A
            // FLAG_NOT_FOCUSABLE window silently refuses keyboard input.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Deprecated in favour of the insets API, which assumes an Activity window with
            // decor. This window has neither, and ADJUST_RESIZE is what actually gets the
            // keyboard to move the typed-reason field into view. Verify on device.
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        // Started before addView, not after: the window being on top of everything does
        // not by itself raise this process's priority, and the gap between adding the
        // window and the freezer next running its check is exactly where a race would
        // otherwise put us back at risk.
        runCatching { InterventionForegroundService.start(service) }
            .onFailure { Log.w(Tag.OVERLAY, "could not start foreground service: ${it.message}") }

        try {
            windowManager.addView(view, params)
        } catch (e: Exception) {
            Log.e(Tag.OVERLAY, "addView failed", e)
            owner.onDestroy()
            isShowing = false
            runCatching { InterventionForegroundService.stop(service) }
            return
        }

        container = view
        lifecycleOwner = owner
        owner.onResume()
        mainHandler.postDelayed(safetyDismiss, SAFETY_TIMEOUT_MS)
        Log.i(Tag.OVERLAY, "shown for ${spec.packageName} (${spec.contextLabel}, night=${spec.isNight})")
    }

    fun hide() {
        mainHandler.removeCallbacks(safetyDismiss)
        mainHandler.post {
            val view = container
            if (view != null) {
                runCatching { windowManager.removeViewImmediate(view) }
                    .onFailure { Log.w(Tag.OVERLAY, "removeView failed: ${it.message}") }
            }
            lifecycleOwner?.onDestroy()
            container = null
            lifecycleOwner = null
            isShowing = false
            // stopService on an already-stopped service is a documented no-op, so this is
            // safe even if show() never got as far as starting it.
            runCatching { InterventionForegroundService.stop(service) }
        }
    }

    private companion object {
        const val SAFETY_TIMEOUT_MS = 5 * 60 * 1000L
    }
}
