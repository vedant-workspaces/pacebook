package com.pacelog.core

import java.time.LocalDate

/**
 * Storage the diary rules run on. The app implements it with Room/SQLite;
 * tests use [InMemoryStore]. Deleting a block deletes its planned activities;
 * deleting a planned activity keeps any linked actual activity but unlinks
 * it (it becomes unplanned).
 */
interface DiaryStore {
    fun <T> transaction(body: () -> T): T

    fun block(id: Long): Block?
    fun insertBlock(b: BlockInput): Long
    fun updateBlock(id: Long, b: BlockInput)
    fun deleteBlock(id: Long)
    fun plannedForBlock(blockId: Long): List<Planned>

    fun planned(id: Long): Planned?
    fun insertPlanned(blockId: Long, p: PlannedInput): Long
    fun updatePlanned(id: Long, p: PlannedInput)
    fun setStatus(id: Long, status: String, reason: String?, notes: String?)
    fun deletePlanned(id: Long)

    fun actual(id: Long): Actual?
    fun actualForPlanned(plannedId: Long): Actual?
    fun insertActual(plannedId: Long?, a: ActualInput, createdAt: Long): Long
    fun updateActual(id: Long, a: ActualInput)
    fun deleteActual(id: Long)
}

class NotFoundException : Exception("Not found")

/** A conflict the runner has to resolve, e.g. skipping a session that has a logged result. */
class ConflictException(message: String) : Exception(message)

/** Metrics recorded when completing (or modifying) a planned activity. */
data class ResultInput(
    val distanceKm: Double?,
    val paceSecPerKm: Int?,
    val avgHeartRate: Int?,
    val comment: String?,
    /** Only for "completed differently": what was actually done. */
    val type: String? = null,
    val date: LocalDate? = null,
)

/** The training-diary rules, independent of storage and UI. */
class Diary(private val store: DiaryStore, private val clock: () -> Long = System::currentTimeMillis) {

    fun createBlock(b: BlockInput): Long = store.insertBlock(b)

    /** Rejects date changes that would leave planned activities outside the block. */
    fun updateBlock(id: Long, b: BlockInput) = store.transaction {
        store.block(id) ?: throw NotFoundException()
        val outside = store.plannedForBlock(id).count { it.date.isBefore(b.startDate) || it.date.isAfter(b.endDate) }
        if (outside > 0) {
            throw ValidationException(
                "startDate",
                "$outside planned ${if (outside == 1) "activity falls" else "activities fall"} outside the new dates. Move or delete them first.",
            )
        }
        store.updateBlock(id, b)
    }

    fun deleteBlock(id: Long) = store.transaction {
        store.block(id) ?: throw NotFoundException()
        store.deleteBlock(id)
    }

    fun addPlanned(blockId: Long, p: PlannedInput): Long = store.transaction {
        store.block(blockId) ?: throw NotFoundException()
        store.insertPlanned(blockId, p)
    }

    fun updatePlanned(id: Long, p: PlannedInput) = store.transaction {
        store.planned(id) ?: throw NotFoundException()
        store.updatePlanned(id, p)
    }

    fun deletePlanned(id: Long) = store.transaction {
        store.planned(id) ?: throw NotFoundException()
        store.deletePlanned(id)
    }

    /** Planned activity done as planned: create or update its linked result. */
    fun complete(plannedId: Long, r: ResultInput) = recordResult(plannedId, r.copy(type = null), Status.COMPLETED)

    /** Planned activity done differently (e.g. tempo → easy run). */
    fun modify(plannedId: Long, r: ResultInput) = recordResult(plannedId, r, Status.MODIFIED)

    private fun recordResult(plannedId: Long, r: ResultInput, status: String) = store.transaction {
        val p = store.planned(plannedId) ?: throw NotFoundException()
        val type = r.type ?: p.type
        val title = if (type == p.type) p.title else ActivityTypes.label(type)
        val input = validateActual(r.date ?: p.date, type, title, r.distanceKm, r.paceSecPerKm, r.avgHeartRate, r.comment)
        val existing = store.actualForPlanned(p.id)
        if (existing == null) store.insertActual(p.id, input, clock()) else store.updateActual(existing.id, input)
        store.setStatus(p.id, status, null, null)
    }

    /** Not completed, with a reason. No actual activity is created. */
    fun skip(plannedId: Long, reason: String?, notes: String?) = store.transaction {
        val (r, n) = validateSkip(reason, notes)
        val p = store.planned(plannedId) ?: throw NotFoundException()
        ensureNoResult(p.id)
        store.setStatus(p.id, Status.SKIPPED, r, n)
    }

    /** Back to "planned" (only when no result is logged). */
    fun reset(plannedId: Long) = store.transaction {
        val p = store.planned(plannedId) ?: throw NotFoundException()
        ensureNoResult(p.id)
        store.setStatus(p.id, Status.PLANNED, null, null)
    }

    private fun ensureNoResult(plannedId: Long) {
        if (store.actualForPlanned(plannedId) != null) {
            throw ConflictException("This activity has a logged result. Delete the logged activity first.")
        }
    }

    /** Logs an actual activity; unplanned unless [plannedId] is given (then the plan is marked completed). */
    fun logActivity(a: ActualInput, plannedId: Long? = null): Long = store.transaction {
        if (plannedId == null) return@transaction store.insertActual(null, a, clock())
        val p = store.planned(plannedId) ?: throw NotFoundException()
        if (store.actualForPlanned(p.id) != null) throw ConflictException("This planned activity already has a logged result")
        val id = store.insertActual(p.id, a, clock())
        store.setStatus(p.id, Status.COMPLETED, null, null)
        id
    }

    /** Edits keep the link to the planned activity. */
    fun updateActivity(id: Long, a: ActualInput) = store.transaction {
        store.actual(id) ?: throw NotFoundException()
        store.updateActual(id, a)
    }

    /** Deleting a linked activity keeps the plan and returns it to "planned". */
    fun deleteActivity(id: Long) = store.transaction {
        val a = store.actual(id) ?: throw NotFoundException()
        store.deleteActual(id)
        a.plannedId?.let { pid -> store.planned(pid)?.let { store.setStatus(it.id, Status.PLANNED, null, null) } }
    }
}

/** Simple in-memory [DiaryStore], used by tests and handy for previews. */
class InMemoryStore : DiaryStore {
    val blocks = mutableMapOf<Long, Block>()
    val planned = mutableMapOf<Long, Planned>()
    val actuals = mutableMapOf<Long, Actual>()
    private var nextId = 1L

    override fun <T> transaction(body: () -> T): T {
        val snapshot = Triple(blocks.toMap(), planned.toMap(), actuals.toMap())
        try {
            return body()
        } catch (e: Throwable) {
            blocks.clear(); blocks.putAll(snapshot.first)
            planned.clear(); planned.putAll(snapshot.second)
            actuals.clear(); actuals.putAll(snapshot.third)
            throw e
        }
    }

    override fun block(id: Long) = blocks[id]
    override fun insertBlock(b: BlockInput) = nextId++.also {
        blocks[it] = Block(it, b.name, b.description, b.startDate, b.endDate, b.goal)
    }
    override fun updateBlock(id: Long, b: BlockInput) {
        blocks[id] = Block(id, b.name, b.description, b.startDate, b.endDate, b.goal)
    }
    override fun deleteBlock(id: Long) {
        planned.values.filter { it.blockId == id }.forEach { deletePlanned(it.id) }
        blocks.remove(id)
    }
    override fun plannedForBlock(blockId: Long) = planned.values.filter { it.blockId == blockId }

    override fun planned(id: Long) = planned[id]
    override fun insertPlanned(blockId: Long, p: PlannedInput) = nextId++.also {
        planned[it] = Planned(it, blockId, p.date, p.type, p.title, p.description, p.distanceKm, p.paceSecPerKm, p.durationSec, Status.PLANNED, null, null)
    }
    override fun updatePlanned(id: Long, p: PlannedInput) {
        val old = planned.getValue(id)
        planned[id] = old.copy(date = p.date, type = p.type, title = p.title, description = p.description,
            plannedDistanceKm = p.distanceKm, plannedPaceSecPerKm = p.paceSecPerKm, plannedDurationSec = p.durationSec)
    }
    override fun setStatus(id: Long, status: String, reason: String?, notes: String?) {
        planned[id] = planned.getValue(id).copy(status = status, reason = reason, notes = notes)
    }
    override fun deletePlanned(id: Long) {
        actuals.values.filter { it.plannedId == id }.forEach { actuals[it.id] = it.copy(plannedId = null) }
        planned.remove(id)
    }

    override fun actual(id: Long) = actuals[id]
    override fun actualForPlanned(plannedId: Long) = actuals.values.firstOrNull { it.plannedId == plannedId }
    override fun insertActual(plannedId: Long?, a: ActualInput, createdAt: Long) = nextId++.also {
        actuals[it] = Actual(it, plannedId, a.date, a.type, a.title, a.distanceKm, a.paceSecPerKm, a.avgHeartRate, a.comment, createdAt)
    }
    override fun updateActual(id: Long, a: ActualInput) {
        val old = actuals.getValue(id)
        actuals[id] = old.copy(date = a.date, type = a.type, title = a.title, distanceKm = a.distanceKm,
            paceSecPerKm = a.paceSecPerKm, avgHeartRate = a.avgHeartRate, comment = a.comment)
    }
    override fun deleteActual(id: Long) {
        actuals.remove(id)
    }
}
