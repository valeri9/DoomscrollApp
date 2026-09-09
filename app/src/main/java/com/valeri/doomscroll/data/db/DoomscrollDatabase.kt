package com.valeri.doomscroll.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.valeri.doomscroll.classifier.MatchType
import com.valeri.doomscroll.classifier.RuleKind

class EnumConverters {
    @TypeConverter fun ruleKindToString(value: RuleKind): String = value.name
    @TypeConverter fun stringToRuleKind(value: String): RuleKind = RuleKind.valueOf(value)
    @TypeConverter fun matchTypeToString(value: MatchType): String = value.name
    @TypeConverter fun stringToMatchType(value: String): MatchType = MatchType.valueOf(value)
}

/**
 * version bumps need a real Migration, not fallbackToDestructiveMigration().
 *
 * This is the only copy of this device's doomscroll log and usage history — there is no
 * server to re-sync from. Room's destructive fallback silently drops every table on a schema
 * mismatch, which trades a loud, obvious crash for a quiet, permanent data loss the user would
 * not notice until they went looking for last month's stats. A crash is recoverable (write the
 * migration, ship a fix); a silent wipe on a personal-data app is not.
 */
@Database(
    entities = [
        MonitoredAppEntity::class,
        ContextRuleEntity::class,
        ReasonEntity::class,
        InterventionEntity::class,
        DailyAppUsageEntity::class,
        UsageSyncStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(EnumConverters::class)
abstract class DoomscrollDatabase : RoomDatabase() {

    abstract fun monitoredApps(): MonitoredAppDao
    abstract fun contextRules(): ContextRuleDao
    abstract fun reasons(): ReasonDao
    abstract fun interventions(): InterventionDao
    abstract fun usage(): UsageDao

    companion object {
        @Volatile private var instance: DoomscrollDatabase? = null

        fun get(context: Context): DoomscrollDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                DoomscrollDatabase::class.java,
                "doomscroll.db",
            ).build().also { instance = it }
        }
    }
}
