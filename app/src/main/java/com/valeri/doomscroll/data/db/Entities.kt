package com.valeri.doomscroll.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.valeri.doomscroll.classifier.MatchType
import com.valeri.doomscroll.classifier.RuleKind

/** An app you've asked to be watched. */
@Entity(tableName = "monitored_apps")
data class MonitoredAppEntity(
    @PrimaryKey val packageName: String,
    val enabled: Boolean = true,
    val addedAt: Long = System.currentTimeMillis(),
)

/**
 * A classification rule. Built-in rules are seeded once and can be disabled but not deleted,
 * so a bad edit is always recoverable.
 */
@Entity(
    tableName = "context_rules",
    indices = [Index(value = ["packageName", "kind"])],
)
data class ContextRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val kind: RuleKind,
    val matchType: MatchType,
    val pattern: String,
    val enabled: Boolean = true,
    val isBuiltIn: Boolean = false,
)

/** A quick-tap answer offered on the "why are you here" prompt. */
@Entity(
    tableName = "reasons",
    indices = [Index(value = ["packageName"])],
)
data class ReasonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** null means it's offered for every app. */
    val packageName: String?,
    val label: String,
    val sortOrder: Int = 0,
)

/** One intervention, start to finish. */
@Entity(
    tableName = "interventions",
    indices = [Index(value = ["localDate"]), Index(value = ["packageName"])],
)
data class InterventionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    /** The rule pattern that matched, kept as text so stats survive rule edits. */
    val contextLabel: String,
    val triggeredAt: Long,
    /** ISO yyyy-MM-dd in the device's zone, so day grouping doesn't need date maths in SQL. */
    val localDate: String,
    val hourOfDay: Int,
    val isNightMode: Boolean,
    val breathingSeconds: Int,
    val reasonLabel: String?,
    val reasonText: String?,
    val continuedAnyway: Boolean,
    val completedAt: Long?,
)

/** Daily foreground time per app, from UsageStatsManager. */
@Entity(tableName = "daily_app_usage", primaryKeys = ["localDate", "packageName"])
data class DailyAppUsageEntity(
    val localDate: String,
    val packageName: String,
    val foregroundMs: Long,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Single-row bookkeeping for the usage importer. */
@Entity(tableName = "usage_sync_state")
data class UsageSyncStateEntity(
    @PrimaryKey val id: Int = 0,
    val backfillCompletedAt: Long?,
    val lastSyncedAt: Long?,
)
