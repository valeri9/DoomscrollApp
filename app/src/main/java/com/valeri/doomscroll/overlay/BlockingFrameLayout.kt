package com.valeri.doomscroll.overlay

import android.content.Context
import android.view.KeyEvent
import android.widget.FrameLayout

/**
 * Swallows BACK so the pause can't be dismissed by reflex. Handled at the view level rather
 * than with Compose's BackHandler because an overlay window has no back-press dispatcher.
 */
class BlockingFrameLayout(context: Context) : FrameLayout(context) {

    var blockBack: Boolean = true

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (blockBack && event.keyCode == KeyEvent.KEYCODE_BACK) return true
        return super.dispatchKeyEvent(event)
    }
}
