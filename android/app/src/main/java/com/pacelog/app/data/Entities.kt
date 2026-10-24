package com.pacelog.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.pacelog.core.Actual
import com.pacelog.core.Block
import com.pacelog.core.Planned
import java.time.LocalDate

/** Dates are stored as epoch days: calendar dates with no time zone to shift them. */
class Converters {
    @TypeConverter fun toDate(v: Long?): LocalDate? = v?.let(LocalDate::ofEpochDay)
    @TypeConverter fun fromDate(d: LocalDate?): Long? = d?.toEpochDay()
}

@Entity(tableName = "blocks", indices = [Index("startDate")])
data class BlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String?,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val goal: String?,
) {
    fun toModel() = Block(id, name, description, startDate, endDate, goal)
}

@Entity(
    tableName = "planned",
    foreignKeys = [ForeignKey(entity = BlockEntity::class, parentColumns = ["id"], childColumns = ["blockId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("blockId"), Index("date")],
)
data class PlannedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val blockId: Long,
    val date: LocalDate,
    val type: String,
    val title: String,
    val description: String?,
    val plannedDistanceKm: Double?,
    val plannedPaceSecPerKm: Int?,
    val plannedDurationSec: Int?,
    val status: String,
    val reason: String?,
    val notes: String?,
) {
    fun toModel() = Planned(id, blockId, date, type, title, description, plannedDistanceKm, plannedPaceSecPerKm,
        plannedDurationSec, status, reason, notes)
}

/** Actual activities. No uniqueness on date: many per day is normal. One result per planned activity. */
@Entity(
    tableName = "activities",
    foreignKeys = [ForeignKey(entity = PlannedEntity::class, parentColumns = ["id"], childColumns = ["plannedId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index(value = ["plannedId"], unique = true), Index("date")],
)
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plannedId: Long?,
    val date: LocalDate,
    val type: String,
    val title: String,
    val distanceKm: Double?,
    val paceSecPerKm: Int?,
    val avgHeartRate: Int?,
    val comment: String?,
    val createdAt: Long,
) {
    fun toModel() = Actual(id, plannedId, date, type, title, distanceKm, paceSecPerKm, avgHeartRate, comment, createdAt)
}
