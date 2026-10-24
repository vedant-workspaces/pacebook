package com.pacelog.core

import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DiaryTest {
    private lateinit var store: InMemoryStore
    private lateinit var diary: Diary
    private val d = { s: String -> LocalDate.parse(s) }

    @BeforeTest
    fun setUp() {
        store = InMemoryStore()
        diary = Diary(store) { 0L }
    }

    private fun block(start: String = "2026-09-28", end: String = "2026-10-25") =
        diary.createBlock(validateBlock("Marathon Block", null, d(start), d(end), "Sub 4:00"))

    private fun plan(blockId: Long, date: String, type: String, km: Double?) =
        diary.addPlanned(blockId, validatePlanned(store.block(blockId)!!, d(date), type, null, null, km, null, null))

    private fun run(date: String, km: Double, pace: Int = 330) =
        diary.logActivity(validateActual(d(date), "easy_run", null, km, pace, null, null))

    @Test
    fun completeCreatesLinkedActivityAndUpdatesInsteadOfDuplicating() {
        val b = block()
        val p = plan(b, "2026-10-04", "easy_run", 8.0)
        diary.complete(p, ResultInput(8.4, 338, 151, "Felt strong today."))
        assertEquals(Status.COMPLETED, store.planned(p)!!.status)
        val a = store.actualForPlanned(p)!!
        assertEquals(8.4, a.distanceKm)
        assertEquals("easy_run", a.type)

        diary.complete(p, ResultInput(8.6, null, null, null))
        assertEquals(1, store.actuals.size)
        assertEquals(8.6, store.actualForPlanned(p)!!.distanceKm)

        assertFailsWith<ValidationException> { diary.complete(p, ResultInput(0.0, null, null, null)) }
    }

    @Test
    fun modifyRecordsWhatWasActuallyDone() {
        val p = plan(block(), "2026-10-04", "tempo", 10.0)
        diary.modify(p, ResultInput(7.0, 355, 152, "Switched to easy", type = "easy_run"))
        val planned = store.planned(p)!!
        assertEquals(Status.MODIFIED, planned.status)
        assertEquals("tempo", planned.type)
        val a = store.actualForPlanned(p)!!
        assertEquals("easy_run", a.type)
        assertEquals("Easy Run", a.title)
    }

    @Test
    fun skipNeedsReasonCreatesNothingAndConflictsWithLoggedResult() {
        val p = plan(block(), "2026-10-03", "tempo", 10.0)
        assertFailsWith<ValidationException> { diary.skip(p, null, null) }
        diary.skip(p, "fatigue", " Legs were still tired. ")
        val s = store.planned(p)!!
        assertEquals(Status.SKIPPED, s.status)
        assertEquals("fatigue", s.reason)
        assertEquals("Legs were still tired.", s.notes)
        assertEquals(0, store.actuals.size)

        diary.reset(p)
        diary.complete(p, ResultInput(10.0, null, null, null))
        assertFailsWith<ConflictException> { diary.skip(p, "work", null) }
    }

    @Test
    fun deletingLinkedActivityRevertsPlanAndDeletingPlanKeepsRun() {
        val b = block()
        val p = plan(b, "2026-10-04", "easy_run", 8.0)
        diary.complete(p, ResultInput(8.4, null, null, null))
        diary.deleteActivity(store.actualForPlanned(p)!!.id)
        assertEquals(Status.PLANNED, store.planned(p)!!.status)

        diary.complete(p, ResultInput(8.0, null, null, null))
        val runId = store.actualForPlanned(p)!!.id
        diary.deleteBlock(b)
        assertNull(store.planned(p))
        assertNull(store.actual(runId)!!.plannedId)
    }

    @Test
    fun linkingThroughLogActivityAndDoubleLinkConflict() {
        val p = plan(block(), "2026-10-04", "easy_run", 8.0)
        val input = validateActual(d("2026-10-04"), "easy_run", null, 8.0, null, null, null)
        diary.logActivity(input, p)
        assertEquals(Status.COMPLETED, store.planned(p)!!.status)
        assertFailsWith<ConflictException> { diary.logActivity(input, p) }
        assertFailsWith<NotFoundException> { diary.logActivity(input, 9999) }
    }

    @Test
    fun editingKeepsLink() {
        val p = plan(block(), "2026-10-04", "intervals", 10.0)
        diary.complete(p, ResultInput(10.2, null, null, null))
        val a = store.actualForPlanned(p)!!
        diary.updateActivity(a.id, validateActual(a.date, "intervals", "Tuesday Intervals", 10.5, null, null, null))
        assertEquals(p, store.actual(a.id)!!.plannedId)
    }

    @Test
    fun blockValidationAndShrinkingPastPlannedActivities() {
        assertFailsWith<ValidationException> { validateBlock(" ", null, d("2026-10-05"), d("2027-01-17"), null) }
        assertFailsWith<ValidationException> { validateBlock("X", null, d("2026-10-05"), d("2026-10-01"), null) }
        val b = block("2026-10-05", "2027-01-17")
        assertFailsWith<ValidationException> {
            validatePlanned(store.block(b)!!, d("2027-02-01"), "easy_run", null, null, null, null, null)
        }
        plan(b, "2026-12-01", "easy_run", 8.0)
        assertFailsWith<ValidationException> {
            diary.updateBlock(b, validateBlock("Shorter", null, d("2026-10-05"), d("2026-11-01"), null))
        }
        assertEquals("Marathon Block", store.block(b)!!.name)
    }

    @Test
    fun multipleActivitiesSameDay() {
        run("2026-10-04", 8.0); run("2026-10-04", 5.0); run("2026-10-04", 3.0)
        val day = dailySeries(store.actuals.values.toList(), emptyList(), d("2026-10-04"), 1).single()
        assertEquals(16.0, day.actualKm)
        assertEquals(3, day.count)
    }

    @Test
    fun failedTransactionRollsBack() {
        val p = plan(block(), "2026-10-04", "easy_run", 8.0)
        assertFailsWith<ValidationException> { diary.complete(p, ResultInput(8.0, 10, null, null)) }
        assertNull(store.actualForPlanned(p))
        assertNotNull(store.planned(p))
        assertEquals(Status.PLANNED, store.planned(p)!!.status)
    }
}
