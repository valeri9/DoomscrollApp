package com.valeri.doomscroll.overlay

/** Everything the overlay needs to run one intervention. */
data class InterventionSpec(
    val packageName: String,
    val contextLabel: String,
    val breathingSeconds: Int,
    val isNight: Boolean,
    /** Extra seconds the Continue button stays disabled once the breathing ends. */
    val continueDelaySeconds: Int = 0,
)

data class InterventionResult(val continuedAnyway: Boolean)
