package com.pacelog.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pacelog.app.data.AppDatabase
import com.pacelog.app.data.Backup
import com.pacelog.app.data.RoomStore
import com.pacelog.core.Actual
import com.pacelog.core.Block
import com.pacelog.core.ConflictException
import com.pacelog.core.Diary
import com.pacelog.core.NotFoundException
import com.pacelog.core.Planned
import com.pacelog.core.ValidationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/** Everything on the device, as plain models. Recomputed whenever the database changes. */
data class Snapshot(val blocks: List<Block>, val planned: List<Planned>, val actuals: List<Actual>) {
    private val blockById = blocks.associateBy { it.id }
    private val plannedById = planned.associateBy { it.id }
    private val actualById = actuals.associateBy { it.id }
    private val actualByPlanned = actuals.filter { it.plannedId != null }.associateBy { it.plannedId!! }

    fun block(id: Long?) = id?.let { blockById[it] }
    fun planned(id: Long?) = id?.let { plannedById[it] }
    fun actual(id: Long?) = id?.let { actualById[it] }
    fun resultOf(plannedId: Long) = actualByPlanned[plannedId]
}

sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Err(val message: String, val field: String? = null) : Outcome<Nothing>
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val dao = db.dao()
    private val diary = Diary(RoomStore(db))

    val state: StateFlow<Snapshot?> = combine(dao.blocksFlow(), dao.plannedFlow(), dao.activitiesFlow()) { b, p, a ->
        Snapshot(b.map { it.toModel() }, p.map { it.toModel() }, a.map { it.toModel() })
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Runs a diary operation off the main thread and turns failures into user-facing messages. */
    suspend fun <T> run(op: (Diary) -> T): Outcome<T> = withContext(Dispatchers.IO) {
        try {
            Outcome.Ok(op(diary))
        } catch (e: ValidationException) {
            Outcome.Err(e.message ?: "Please check this field.", e.field)
        } catch (e: ConflictException) {
            Outcome.Err(e.message ?: "That can't be done right now.")
        } catch (e: NotFoundException) {
            Outcome.Err("This item no longer exists.")
        } catch (e: Exception) {
            Outcome.Err("Unable to save. Please try again.")
        }
    }

    suspend fun exportBackup(uri: Uri): Outcome<Unit> = withContext(Dispatchers.IO) {
        try {
            val text = Backup.export(db, LocalDateTime.now().toString())
            getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
            Outcome.Ok(Unit)
        } catch (e: Exception) {
            Outcome.Err("Couldn't save the backup file.")
        }
    }

    suspend fun restoreBackup(uri: Uri): Outcome<Int> = withContext(Dispatchers.IO) {
        try {
            val text = getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            Outcome.Ok(Backup.restore(db, text))
        } catch (e: IllegalArgumentException) {
            Outcome.Err(e.message ?: "This isn't a Pacelog backup file.")
        } catch (e: Exception) {
            Outcome.Err("Couldn't read the backup file.")
        }
    }
}
