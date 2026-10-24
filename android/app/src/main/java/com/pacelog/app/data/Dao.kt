package com.pacelog.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface PacelogDao {
    // Observed by the UI.
    @Query("SELECT * FROM blocks ORDER BY startDate DESC, id DESC")
    fun blocksFlow(): Flow<List<BlockEntity>>

    @Query("SELECT * FROM planned ORDER BY date, id")
    fun plannedFlow(): Flow<List<PlannedEntity>>

    @Query("SELECT * FROM activities ORDER BY date DESC, createdAt DESC, id DESC")
    fun activitiesFlow(): Flow<List<ActivityEntity>>

    // Blocks.
    @Query("SELECT * FROM blocks WHERE id = :id")
    fun block(id: Long): BlockEntity?

    @Insert
    fun insertBlock(b: BlockEntity): Long

    @Update
    fun updateBlock(b: BlockEntity)

    @Query("DELETE FROM blocks WHERE id = :id")
    fun deleteBlock(id: Long)

    // Planned activities.
    @Query("SELECT * FROM planned WHERE blockId = :blockId")
    fun plannedForBlock(blockId: Long): List<PlannedEntity>

    @Query("SELECT * FROM planned WHERE id = :id")
    fun planned(id: Long): PlannedEntity?

    @Insert
    fun insertPlanned(p: PlannedEntity): Long

    @Query(
        """UPDATE planned SET date = :date, type = :type, title = :title, description = :description,
           plannedDistanceKm = :km, plannedPaceSecPerKm = :pace, plannedDurationSec = :duration WHERE id = :id""",
    )
    fun updatePlanned(id: Long, date: LocalDate, type: String, title: String, description: String?, km: Double?, pace: Int?, duration: Int?)

    @Query("UPDATE planned SET status = :status, reason = :reason, notes = :notes WHERE id = :id")
    fun setStatus(id: Long, status: String, reason: String?, notes: String?)

    @Query("DELETE FROM planned WHERE id = :id")
    fun deletePlanned(id: Long)

    @Query("DELETE FROM planned WHERE blockId = :blockId")
    fun deletePlannedForBlock(blockId: Long)

    // Actual activities.
    @Query("SELECT * FROM activities WHERE id = :id")
    fun activity(id: Long): ActivityEntity?

    @Query("SELECT * FROM activities WHERE plannedId = :plannedId LIMIT 1")
    fun activityForPlanned(plannedId: Long): ActivityEntity?

    @Insert
    fun insertActivity(a: ActivityEntity): Long

    @Update
    fun updateActivity(a: ActivityEntity)

    @Query("DELETE FROM activities WHERE id = :id")
    fun deleteActivity(id: Long)

    @Query("UPDATE activities SET plannedId = NULL WHERE plannedId = :plannedId")
    fun unlinkFromPlanned(plannedId: Long)

    @Query("UPDATE activities SET plannedId = NULL WHERE plannedId IN (SELECT id FROM planned WHERE blockId = :blockId)")
    fun unlinkFromBlock(blockId: Long)

    // Backup / restore.
    @Query("SELECT * FROM blocks") fun allBlocks(): List<BlockEntity>
    @Query("SELECT * FROM planned") fun allPlanned(): List<PlannedEntity>
    @Query("SELECT * FROM activities") fun allActivities(): List<ActivityEntity>
    @Insert fun insertBlocks(items: List<BlockEntity>)
    @Insert fun insertPlannedList(items: List<PlannedEntity>)
    @Insert fun insertActivities(items: List<ActivityEntity>)
    @Query("DELETE FROM activities") fun clearActivities()
    @Query("DELETE FROM planned") fun clearPlanned()
    @Query("DELETE FROM blocks") fun clearBlocks()
}
