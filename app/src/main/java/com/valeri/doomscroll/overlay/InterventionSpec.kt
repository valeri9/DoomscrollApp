package com.valeri.doomscroll.overlay

/** Everything the overlay needs to run one intervention. */
data class InterventionSpec(
    val packageName: String,
    val contextLabel: String,
    val breathingSeconds: Int,
    val isNight: Boolean,
    val reasons: List<String>,
    /** Night mode asks for a sentence instead of a tap. */
    val requireTypedReason: Boolean = false,
    val minReasonChars: Int = 15,
    /** Extra seconds the Continue button stays disabled after a reason is given. */
    val continueDelaySeconds: Int = 0,
)

data class InterventionResult(
    val reasonLabel: String?,
    val reasonText: String?,
    val continuedAnyway: Boolean,
)
