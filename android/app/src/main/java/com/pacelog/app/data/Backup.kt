package com.pacelog.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/*
 * Backup file: plain JSON with ISO dates, so it is readable and can be
 * restored on another phone. Restoring replaces everything on the device.
 */

@Serializable
data class BackupFile(
    val app: String = "pacelog",
    val version: Int = 1,
    val exportedAt: String,
    val blocks: List<BlockRow>,
    val planned: List<PlannedRow>,
    val activities: List<ActivityRow>,
)

@Serializable
data class BlockRow(val id: Long, val name: String, val description: String? = null, val startDate: String, val endDate: String, val goal: String? = null)

@Serializable
data class PlannedRow(
    val id: Long, val blockId: Long, val date: String, val type: String, val title: String,
    val description: String? = null, val plannedDistanceKm: Double? = null, val plannedPaceSecPerKm: Int? = null,
    val plannedDurationSec: Int? = null, val status: String, val reason: String? = null, val notes: String? = null,
)

@Serializable
data class ActivityRow(
    val id: Long, val plannedId: Long? = null, val date: String, val type: String, val title: String,
    val distanceKm: Double? = null, val paceSecPerKm: Int? = null, val avgHeartRate: Int? = null,
    val comment: String? = null, val createdAt: Long,
)

private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

object Backup {
    fun export(db: AppDatabase, exportedAt: String): String {
        val dao = db.dao()
        val file = BackupFile(
            exportedAt = exportedAt,
            blocks = dao.allBlocks().map { BlockRow(it.id, it.name, it.description, it.startDate.toString(), it.endDate.toString(), it.goal) },
            planned = dao.allPlanned().map {
                PlannedRow(it.id, it.blockId, it.date.toString(), it.type, it.title, it.description, it.plannedDistanceKm,
                    it.plannedPaceSecPerKm, it.plannedDurationSec, it.status, it.reason, it.notes)
            },
            activities = dao.allActivities().map {
                ActivityRow(it.id, it.plannedId, it.date.toString(), it.type, it.title, it.distanceKm, it.paceSecPerKm,
                    it.avgHeartRate, it.comment, it.createdAt)
            },
        )
        return json.encodeToString(BackupFile.serializer(), file)
    }

    /** Parses and validates the whole file before touching the database. Returns the activity count. */
    fun restore(db: AppDatabase, text: String): Int {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("This isn't a Pacelog backup file.")
        }
        if (file.app != "pacelog") throw IllegalArgumentException("This isn't a Pacelog backup file.")
        val blocks = file.blocks.map { BlockEntity(it.id, it.name, it.description, LocalDate.parse(it.startDate), LocalDate.parse(it.endDate), it.goal) }
        val blockIds = blocks.map { it.id }.toSet()
        val planned = file.planned.filter { it.blockId in blockIds }.map {
            PlannedEntity(it.id, it.blockId, LocalDate.parse(it.date), it.type, it.title, it.description, it.plannedDistanceKm,
                it.plannedPaceSecPerKm, it.plannedDurationSec, it.status, it.reason, it.notes)
        }
        val plannedIds = planned.map { it.id }.toSet()
        val seenLinks = mutableSetOf<Long>()
        val activities = file.activities.map {
            val link = it.plannedId?.takeIf { p -> p in plannedIds && seenLinks.add(p) }
            ActivityEntity(it.id, link, LocalDate.parse(it.date), it.type, it.title, it.distanceKm, it.paceSecPerKm,
                it.avgHeartRate, it.comment, it.createdAt)
        }
        db.runInTransaction(Runnable {
            val dao = db.dao()
            dao.clearActivities(); dao.clearPlanned(); dao.clearBlocks()
            dao.insertBlocks(blocks); dao.insertPlannedList(planned); dao.insertActivities(activities)
        })
        return activities.size
    }
}
