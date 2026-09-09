package com.valeri.doomscroll.learn

import android.view.accessibility.AccessibilityNodeInfo

/** One interesting node found on screen during a Learn Mode capture. */
data class CapturedNode(
    val viewId: String,
    val className: String,
    val depth: Int,
    val scrollable: Boolean,
    val editable: Boolean,
    val childCount: Int,
) {
    /** The part of the id that rules match on, e.g. "clips_viewer_view_pager". */
    val shortId: String get() = viewId.substringAfter(":id/", viewId)
}

/**
 * Walks the full accessibility hierarchy and collects every node carrying a resource id.
 *
 * This is the one place in the app that does a full tree traversal, and it only ever runs
 * when you explicitly ask for a capture — never on the event hot path.
 */
object NodeInspector {

    private const val MAX_DEPTH = 40
    private const val MAX_NODES = 600

    fun capture(root: AccessibilityNodeInfo?): List<CapturedNode> {
        if (root == null) return emptyList()
        val out = LinkedHashMap<String, CapturedNode>()
        walk(root, 0, out)
        return out.values.sortedWith(
            compareByDescending<CapturedNode> { it.scrollable }.thenBy { it.depth }
        )
    }

    private fun walk(node: AccessibilityNodeInfo, depth: Int, out: MutableMap<String, CapturedNode>) {
        if (depth > MAX_DEPTH || out.size >= MAX_NODES) return

        val id = node.viewIdResourceName
        if (!id.isNullOrBlank() && node.isVisibleToUser) {
            out.getOrPut(id) {
                CapturedNode(
                    viewId = id,
                    className = node.className?.toString().orEmpty(),
                    depth = depth,
                    scrollable = node.isScrollable,
                    editable = node.isEditable,
                    childCount = node.childCount,
                )
            }
        }

        for (i in 0 until node.childCount) {
            val child = try {
                node.getChild(i)
            } catch (_: Exception) {
                null
            } ?: continue
            walk(child, depth + 1, out)
            // recycle() is a no-op on API 33+ but the pool is real on minSdk 26-32, and a
            // capture that retries on every event while a slow screen settles can otherwise
            // run several full 600-node traversals in a row without releasing any of them.
            @Suppress("DEPRECATION")
            child.recycle()
        }
    }
}
