package com.valeri.doomscroll.classifier

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.valeri.doomscroll.service.Tag

/**
 * Decides whether the screen currently on top of a monitored app is a doomscroll feed,
 * legitimate use, or unrecognised.
 *
 * Evaluation order matters and is deliberate: LEGIT rules are checked first and win
 * outright. If a screen looks like both a feed and a DM thread, we treat it as a DM and
 * stay silent.
 */
class RuleEngine(initialRules: List<ContextRule> = BuiltInRules.all) {

    @Volatile
    private var rulesByPackage: Map<String, List<ContextRule>> = index(initialRules)

    /** Swapped in whenever the rule set is edited; classification reads a single reference. */
    fun updateRules(rules: List<ContextRule>) {
        rulesByPackage = index(rules)
    }

    private fun index(rules: List<ContextRule>) =
        rules.filter { it.enabled }.groupBy { it.packageName }

    fun classify(
        packageName: String,
        windowClassName: CharSequence?,
        root: AccessibilityNodeInfo?,
    ): Classification {
        val rules = rulesByPackage[packageName] ?: return Classification(ScreenClass.NEUTRAL, null)

        // Pass 1 — window class name. Free: the event already carries it, no node access.
        val className = windowClassName?.toString().orEmpty()
        if (className.isNotEmpty()) {
            classifyByClassName(rules, className, RuleKind.LEGIT)?.let { return it }
        }

        // Pass 2 — indexed view-id lookups. Only touches the tree if we have one.
        if (root != null) {
            matchViewId(rules, root, RuleKind.LEGIT)?.let { return it }
        }

        if (className.isNotEmpty()) {
            classifyByClassName(rules, className, RuleKind.DOOMSCROLL)?.let { return it }
        }
        if (root != null) {
            matchViewId(rules, root, RuleKind.DOOMSCROLL)?.let { return it }
        }

        return Classification(ScreenClass.NEUTRAL, null)
    }

    private fun classifyByClassName(
        rules: List<ContextRule>,
        className: String,
        kind: RuleKind,
    ): Classification? = rules
        .firstOrNull { it.kind == kind && it.match == MatchType.CLASS_CONTAINS && className.contains(it.pattern, ignoreCase = true) }
        ?.let { Classification(it.kind.toScreenClass(), it) }

    private fun matchViewId(
        rules: List<ContextRule>,
        root: AccessibilityNodeInfo,
        kind: RuleKind,
    ): Classification? {
        for (rule in rules) {
            if (rule.kind != kind || rule.match != MatchType.VIEW_ID) continue
            val hits = try {
                root.findAccessibilityNodeInfosByViewId(rule.fullViewId)
            } catch (e: Exception) {
                // The window can vanish mid-query; that is normal, not an error worth surfacing.
                Log.v(Tag.CLASSIFIER, "view id lookup failed for ${rule.fullViewId}: ${e.message}")
                null
            } ?: continue

            // No recycle(): AccessibilityNodeInfo pooling was removed in API 33 and the call
            // is a deprecated no-op.
            if (hits.any { it != null && it.isVisibleToUser }) {
                return Classification(rule.kind.toScreenClass(), rule)
            }
        }
        return null
    }

    private fun RuleKind.toScreenClass() = when (this) {
        RuleKind.DOOMSCROLL -> ScreenClass.DOOMSCROLL
        RuleKind.LEGIT -> ScreenClass.LEGIT
    }

    data class Classification(val screenClass: ScreenClass, val matchedRule: ContextRule?) {
        val describe: String
            get() = matchedRule?.let { "${screenClass} via ${it.match}:${it.pattern}" } ?: screenClass.name
    }
}
