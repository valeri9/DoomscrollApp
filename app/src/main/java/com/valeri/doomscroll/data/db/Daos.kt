package com.valeri.doomscroll.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitoredAppDao {
    @Query("SELECT * FROM monitored_apps ORDER BY addedAt")
    fun observeAll(): Flow<List<MonitoredAppEntity>>

    @Query("SELECT * FROM monitored_apps")
    suspend fun getAll(): List<MonitoredAppEntity>

    @Upsert
    suspend fun upsert(app: MonitoredAppEntity)

    @Query("DELETE FROM monitored_apps WHERE packageName = :packageName")
    suspend fun remove(packageName: String)

    @Query("SELECT COUNT(*) FROM monitored_apps")
    suspend fun count(): Int
}

@Dao
interface ContextRuleDao {
    @Query("SELECT * FROM context_rules ORDER BY isBuiltIn DESC, id")
    fun observeAll(): Flow<List<ContextRuleEntity>>

    @Query("SELECT * FROM context_rules WHERE packageName = :packageName ORDER BY isBuiltIn DESC, id")
    fun observeForPackage(packageName: String): Flow<List<ContextRuleEntity>>

    @Query("SELECT * FROM context_rules")
    suspend fun getAll(): List<ContextRuleEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rules: List<ContextRuleEntity>)

    @Insert
    suspend fun insert(rule: ContextRuleEntity): Long

    @Update
    suspend fun update(rule: ContextRuleEntity)

    @Delete
    suspend fun delete(rule: ContextRuleEntity)

    @Query("SELECT COUNT(*) FROM context_rules")
    suspend fun count(): Int

    @Query("DELETE FROM context_rules WHERE isBuiltIn = 1")
    suspend fun deleteBuiltIns()
}

@Dao
interface ReasonDao {
    @Query("SELECT * FROM reasons WHERE packageName = :packageName OR packageName IS NULL ORDER BY sortOrder, id")
    suspend fun forPackage(packageName: String): List<ReasonEntity>

    @Query("SELECT * FROM reasons WHERE packageName = :packageName OR packageName IS NULL ORDER BY sortOrder, id")
    fun observeForPackage(packageName: String): Flow<List<ReasonEntity>>

    @Insert
    suspend fun insert(reason: ReasonEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(reasons: List<ReasonEntity>)

    @Delete
    suspend fun delete(reason: ReasonEntity)

    @Query("SELECT COUNT(*) FROM reasons")
    suspend fun count(): Int
}

@Dao
interface InterventionDao {
    @Insert
    suspend fun insert(intervention: InterventionEntity): Long

    @Query("UPDATE interventions SET reasonLabel = :label, reasonText = :text, continuedAnyway = :continued, completedAt = :completedAt WHERE id = :id")
    suspend fun complete(id: Long, label: String?, text: String?, continued: Boolean, completedAt: Long)

    @Query("SELECT * FROM interventions ORDER BY triggeredAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<InterventionEntity>>

    @Query("SELECT localDate, COUNT(*) AS count FROM interventions WHERE localDate >= :fromDate GROUP BY localDate")
    fun observeDailyCounts(fromDate: String): Flow<List<DailyCount>>

    @Query("SELECT packageName, COUNT(*) AS count FROM interventions WHERE localDate >= :fromDate GROUP BY packageName")
    fun observePackageCounts(fromDate: String): Flow<List<PackageCount>>

    @Query("SELECT reasonLabel AS label, COUNT(*) AS count FROM interventions WHERE reasonLabel IS NOT NULL AND localDate >= :fromDate GROUP BY reasonLabel ORDER BY count DESC")
    fun observeReasonCounts(fromDate: String): Flow<List<ReasonCount>>

    @Query("SELECT COUNT(*) FROM interventions WHERE isNightMode = 1 AND localDate >= :fromDate")
    fun observeNightCount(fromDate: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM interventions WHERE localDate >= :fromDate")
    fun observeTotalCount(fromDate: String): Flow<Int>

    /** Interventions already shown tonight, used to escalate repeat visits. */
    @Query("SELECT COUNT(*) FROM interventions WHERE isNightMode = 1 AND triggeredAt >= :since")
    suspend fun countNightSince(since: Long): Int
}

@Dao
interface UsageDao {
    @Upsert
    suspend fun upsertAll(rows: List<DailyAppUsageEntity>)

    @Query("SELECT * FROM daily_app_usage WHERE localDate >= :fromDate ORDER BY localDate")
    fun observeSince(fromDate: String): Flow<List<DailyAppUsageEntity>>

    @Query("SELECT localDate, SUM(foregroundMs) AS totalMs FROM daily_app_usage WHERE localDate >= :fromDate GROUP BY localDate ORDER BY localDate")
    fun observeDailyTotals(fromDate: String): Flow<List<DailyTotal>>

    @Query("SELECT packageName, SUM(foregroundMs) AS totalMs FROM daily_app_usage WHERE localDate >= :fromDate GROUP BY packageName ORDER BY totalMs DESC")
    fun observePackageTotals(fromDate: String): Flow<List<PackageTotal>>

    @Query("SELECT * FROM usage_sync_state WHERE id = 0")
    suspend fun syncState(): UsageSyncStateEntity?

    @Upsert
    suspend fun setSyncState(state: UsageSyncStateEntity)
}

data class DailyCount(val localDate: String, val count: Int)
data class PackageCount(val packageName: String, val count: Int)
data class ReasonCount(val label: String, val count: Int)
data class DailyTotal(val localDate: String, val totalMs: Long)
data class PackageTotal(val packageName: String, val totalMs: Long)
