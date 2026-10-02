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

    @Query("SELECT * FROM interventions WHERE localDate >= :fromDate")
    fun observeSince(fromDate: String): Flow<List<InterventionEntity>>

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

    @Query("SELECT * FROM usage_sync_state WHERE id = 0")
    suspend fun syncState(): UsageSyncStateEntity?

    @Upsert
    suspend fun setSyncState(state: UsageSyncStateEntity)
}

@Dao
interface DoomscrollTimeDao {
    /**
     * Adds to the day's running total in one statement. Plain INSERT OR REPLACE with a
     * subselect rather than SQLite's ON CONFLICT ... DO UPDATE upsert, which needs SQLite 3.24
     * and minSdk 26 ships 3.18.
     */
    @Query(
        "INSERT OR REPLACE INTO doomscroll_time (localDate, packageName, ms) VALUES (:localDate, :packageName, " +
            "COALESCE((SELECT ms FROM doomscroll_time WHERE localDate = :localDate AND packageName = :packageName), 0) + :ms)"
    )
    suspend fun add(localDate: String, packageName: String, ms: Long)

    @Query("SELECT * FROM doomscroll_time WHERE localDate >= :fromDate")
    fun observeSince(fromDate: String): Flow<List<DoomscrollTimeEntity>>

    @Query("SELECT MIN(localDate) FROM doomscroll_time")
    fun observeFirstDate(): Flow<String?>
}
