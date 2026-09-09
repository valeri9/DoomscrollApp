package com.valeri.doomscroll.learn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridge between the Learn Mode UI and the accessibility service.
 *
 * The service and the UI live in the same process, so a plain singleton is the whole
 * mechanism — no IPC, no binder, no broadcast.
 *
 * Flow: the UI arms a capture with a deadline, you switch to the app you want to teach it
 * about, and the next window event inside that deadline dumps the hierarchy here.
 */
object LearnMode {

    data class Capture(
        val packageName: String,
        val windowClassName: String,
        val nodes: List<CapturedNode>,
        val capturedAt: Long = System.currentTimeMillis(),
    )

    private val _armedUntil = MutableStateFlow(0L)
    val armedUntil: StateFlow<Long> = _armedUntil.asStateFlow()

    private val _lastCapture = MutableStateFlow<Capture?>(null)
    val lastCapture: StateFlow<Capture?> = _lastCapture.asStateFlow()

    val isArmed: Boolean get() = System.currentTimeMillis() < _armedUntil.value

    fun arm(windowMillis: Long) {
        _armedUntil.value = System.currentTimeMillis() + windowMillis
    }

    fun disarm() {
        _armedUntil.value = 0L
    }

    fun record(capture: Capture) {
        _lastCapture.value = capture
        disarm()
    }
}
