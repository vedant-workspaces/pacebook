package com.pacelog.app.data

import com.pacelog.core.ActualInput
import com.pacelog.core.BlockInput
import com.pacelog.core.DiaryStore
import com.pacelog.core.PlannedInput
import com.pacelog.core.Status
import java.util.concurrent.Callable

/** [DiaryStore] on SQLite. Call from a background thread. */
class RoomStore(private val db: AppDatabase) : DiaryStore {
    private val dao = db.dao()

    override fun <T> transaction(body: () -> T): T = db.runInTransaction(Callable { body() })

    override fun block(id: Long) = dao.block(id)?.toModel()
    override fun insertBlock(b: BlockInput) =
        dao.insertBlock(BlockEntity(name = b.name, description = b.description, startDate = b.startDate, endDate = b.endDate, goal = b.goal))
    override fun updateBlock(id: Long, b: BlockInput) =
        dao.updateBlock(BlockEntity(id, b.name, b.description, b.startDate, b.endDate, b.goal))
    override fun deleteBlock(id: Long) {
        // Runs survive: unlink them before their planned activities go.
        dao.unlinkFromBlock(id)
        dao.deletePlannedForBlock(id)
        dao.deleteBlock(id)
    }
    override fun plannedForBlock(blockId: Long) = dao.plannedForBlock(blockId).map { it.toModel() }

    override fun planned(id: Long) = dao.planned(id)?.toModel()
    override fun insertPlanned(blockId: Long, p: PlannedInput) = dao.insertPlanned(
        PlannedEntity(blockId = blockId, date = p.date, type = p.type, title = p.title, description = p.description,
            plannedDistanceKm = p.distanceKm, plannedPaceSecPerKm = p.paceSecPerKm, plannedDurationSec = p.durationSec,
            status = Status.PLANNED, reason = null, notes = null),
    )
    override fun updatePlanned(id: Long, p: PlannedInput) =
        dao.updatePlanned(id, p.date, p.type, p.title, p.description, p.distanceKm, p.paceSecPerKm, p.durationSec)
    override fun setStatus(id: Long, status: String, reason: String?, notes: String?) = dao.setStatus(id, status, reason, notes)
    override fun deletePlanned(id: Long) {
        dao.unlinkFromPlanned(id)
        dao.deletePlanned(id)
    }

    override fun actual(id: Long) = dao.activity(id)?.toModel()
    override fun actualForPlanned(plannedId: Long) = dao.activityForPlanned(plannedId)?.toModel()
    override fun insertActual(plannedId: Long?, a: ActualInput, createdAt: Long) = dao.insertActivity(
        ActivityEntity(plannedId = plannedId, date = a.date, type = a.type, title = a.title, distanceKm = a.distanceKm,
            paceSecPerKm = a.paceSecPerKm, avgHeartRate = a.avgHeartRate, comment = a.comment, createdAt = createdAt),
    )
    override fun updateActual(id: Long, a: ActualInput) {
        val old = dao.activity(id) ?: return
        dao.updateActivity(old.copy(date = a.date, type = a.type, title = a.title, distanceKm = a.distanceKm,
            paceSecPerKm = a.paceSecPerKm, avgHeartRate = a.avgHeartRate, comment = a.comment))
    }
    override fun deleteActual(id: Long) = dao.deleteActivity(id)
}
