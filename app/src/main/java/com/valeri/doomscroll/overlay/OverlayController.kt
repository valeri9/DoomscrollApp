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

    val isShowing: Boolean get() = container != null

    fun show(packageName: String, contextLabel: String, breathingSeconds: Int) {
        if (isShowing) return
        if (!Settings.canDrawOverlays(service)) {
            Log.w(Tag.OVERLAY, "SYSTEM_ALERT_WINDOW not granted; cannot show intervention")
            return
        }
        mainHandler.post { showInternal(packageName, contextLabel, breathingSeconds) }
    }

    private fun showInternal(packageName: String, contextLabel: String, breathingSeconds: Int) {
        val owner = OverlayLifecycleOwner().apply { onCreate() }
        val view = BlockingFrameLayout(service)

        val compose = ComposeView(service).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                InterventionScreen(
                    packageName = packageName,
                    contextLabel = contextLabel,
                    breathingSeconds = breathingSeconds,
                    onDismiss = { reason -> onCompleted(packageName, contextLabel, reason) },
                )
            }
        }
        view.addView(compose)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Focusable on purpose: it makes the window modal, lets us intercept BACK, and is
            // a prerequisite for the typed-reason field in the night-mode flow.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            windowManager.addView(view, params)
        } catch (e: Exception) {
            Log.e(Tag.OVERLAY, "addView failed", e)
            owner.onDestroy()
            return
        }

        container = view
        lifecycleOwner = owner
        owner.onResume()
        mainHandler.postDelayed(safetyDismiss, SAFETY_TIMEOUT_MS)
        Log.i(Tag.OVERLAY, "shown for $packageName ($contextLabel)")
    }

    private fun onCompleted(packageName: String, contextLabel: String, reason: String?) {
        // Phase 2 persists this to Room; for now it goes to logcat so the checkpoint is verifiable.
        Log.i(Tag.OVERLAY, "completed pkg=$packageName context=$contextLabel reason=${reason ?: "-"}")
        hide()
    }

    fun hide() {
        mainHandler.removeCallbacks(safetyDismiss)
        mainHandler.post {
            val view = container ?: return@post
            runCatching { windowManager.removeViewImmediate(view) }
                .onFailure { Log.w(Tag.OVERLAY, "removeView failed: ${it.message}") }
            lifecycleOwner?.onDestroy()
            container = null
            lifecycleOwner = null
        }
    }

    private companion object {
        const val SAFETY_TIMEOUT_MS = 5 * 60 * 1000L
    }
}
