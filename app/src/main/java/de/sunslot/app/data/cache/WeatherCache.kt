package de.sunslot.app.data.cache

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Room entity for cached hourly weather records.
 */
@Entity(tableName = "cached_hourly_weather")
data class CachedHourRecord(
    @PrimaryKey val timestamp: Long,
    val temperature: Double,
    val precipitation: Double,
    val precipitationProbability: Double,
    val sunshine: Double,
    val cloudCover: Double,
    val windSpeed: Double,
    val condition: String?,
    val lat: Double,
    val lon: Double,
    val fetchedAt: Long
)

@Dao
interface WeatherCacheDao {

    @Query(
        """
        SELECT * FROM cached_hourly_weather 
        WHERE timestamp >= :from AND timestamp < :until AND lat = :lat AND lon = :lon
        ORDER BY timestamp ASC
        """
    )
    suspend fun getRecordsForRange(
        from: Long,
        until: Long,
        lat: Double,
        lon: Double
    ): List<CachedHourRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<CachedHourRecord>)

    @Query("DELETE FROM cached_hourly_weather WHERE fetchedAt < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)
}

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): LocalDateTime? =
        value?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }

    @TypeConverter
    fun dateToTimestamp(date: LocalDateTime?): Long? =
        date?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
}

@Database(entities = [CachedHourRecord::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class WeatherCacheDatabase : RoomDatabase() {
    abstract fun weatherCacheDao(): WeatherCacheDao
}
