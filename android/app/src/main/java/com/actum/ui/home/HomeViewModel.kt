package com.actum.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.actum.ActumApp
import com.actum.data.Memory
import com.actum.embed.EmbedWorker
import com.actum.embed.LocalSearch
import com.actum.embed.VectorIndex
import com.actum.imports.BulkImporter
import com.actum.net.SearchOutcome
import com.actum.data.DiaryEntry
import com.actum.data.DiaryVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.actum.data.Expense
import com.actum.data.LogRecord
import com.actum.data.Lesson
import com.actum.data.Marker
import com.actum.data.TodoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the home screen holds. The memories come straight from Room,
 * newest first, so a save made in another process shows up by itself; null
 * until Room has answered, so the screen draws nothing rather than flash
 * "Nothing kept yet".
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ActumApp

    val memories: StateFlow<List<Memory>?> = app.database.memoryDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Actum: asking for something to be done, and the lists confirmed actions fill. */
    val actor get() = app.actor
    private val lists = app.database.listsDao()
    val todos: StateFlow<List<TodoItem>> = lists.todos().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val expenses: StateFlow<List<Expense>> = lists.expenses().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setDone(item: TodoItem, done: Boolean) {
        viewModelScope.launch { lists.setDone(item.id, done) }
    }

    fun delete(item: TodoItem) {
        viewModelScope.launch { lists.deleteTodo(item.id) }
    }

    fun delete(expense: Expense) {
        viewModelScope.launch { lists.deleteExpense(expense.id) }
    }

    /** Actum's own calendar: the days of the events and reminders confirmed here. */
    val markers: StateFlow<List<Marker>> = lists.markers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(marker: Marker) {
        viewModelScope.launch { lists.deleteMarker(marker.id) }
    }

    /** Whether the diary has a password yet. */
    val diaryHasPassword = MutableStateFlow(app.diaryVault.hasPassword)

    /**
     * Sets the diary's first password, and seals to it whatever was written
     * before there was one. Off the main thread: stretching a password takes
     * about a second, on purpose.
     */
    fun setDiaryPassword(password: String, done: (DiaryVault.Reader) -> Unit) {
        viewModelScope.launch {
            val reader = withContext(Dispatchers.Default) {
                val reader = app.diaryVault.setPassword(password.toCharArray())
                for (entry in lists.diaryNow()) {
                    if (DiaryVault.isSealed(entry.sealed)) continue
                    val words = app.diaryKeystore.open(entry.sealed)?.toString(Charsets.UTF_8) ?: continue
                    app.diaryVault.seal(words)?.let { lists.reseal(entry.id, it) }
                }
                reader
            }
            diaryHasPassword.value = true
            done(reader)
        }
    }

    /** [done] gets the open diary, or null when [password] is not the password. */
    fun unlockDiary(password: String, done: (DiaryVault.Reader?) -> Unit) {
        viewModelScope.launch { done(withContext(Dispatchers.Default) { app.diaryVault.unlock(password.toCharArray()) }) }
    }

    /** [done] gets false, with nothing changed, when [old] is not the password. */
    fun changeDiaryPassword(old: String, new: String, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            done(withContext(Dispatchers.Default) { app.diaryVault.changePassword(old.toCharArray(), new.toCharArray()) })
        }
    }

    /** The diary as stored: still sealed. A [DiaryVault.Reader] opens an entry once the person has unlocked. */
    val diary: StateFlow<List<DiaryEntry>> = lists.diary().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(entry: DiaryEntry) {
        viewModelScope.launch { lists.deleteDiary(entry.id) }
    }

    private val logFilter = MutableStateFlow("")

    fun setLogFilter(words: String) {
        logFilter.value = words
    }

    /** What was confirmed, newest first, narrowed to the records holding the typed words. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    /** What the rules could learn, newest first. */
    val lessons: StateFlow<List<Lesson>> = app.database.lessonDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun forgetLessons() {
        app.applicationScope.launch { app.database.lessonDao().clear() }
    }

    val log: StateFlow<List<LogRecord>> = logFilter
        .flatMapLatest { lists.log(it.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Bringing in what is already on the phone; its line shows over the search field. */
    internal val importer: BulkImporter get() = app.importer

    /** Held here, not by the composition, so a rotation replays no entrance. */
    internal val entrances = MosaicEntrances()

    /** Which result rows have arrived in the search on screen; held here for the same reason. */
    internal val arrivals = SearchArrivals()

    private val typed = MutableStateFlow("")

    /** What is typed in the search field. */
    fun setQuery(query: String) {
        typed.value = query
    }

    /**
     * The search: the phone's own words at once, then the ranking
     * after a pause. The ranking runs on the phone (local-first L2); only the
     * phrase goes to the server, to be parsed. Until the model is on the phone,
     * the server ranks, and the words stand alone when it is out of reach.
     */
    private val localSearch = LocalSearch(
        embed = { app.embedder.text(it) },
        vectors = VectorIndex(app.database.embeddingDao())::current,
        words = { query, limit -> app.database.readingDao().search(query, limit) },
        // No server to parse the phrase into filters: the ranking alone.
        parse = { null },
        memories = { app.database.memoryDao().getAll() },
    )

    val search: StateFlow<SearchState> = twoStageSearch(
        queries = typed,
        wordSearch = { app.database.readingDao().search(it, MATCHES) },
        serverSearch = { localSearch.search(it, MATCHES) ?: SearchOutcome.Unavailable(NO_MODEL, offline = false) },
        heldMemories = { app.database.memoryDao().getAll() },
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState.Resting)

    /** Delete with five seconds of Undo; its changes outlive the screen. */
    private val deletes = UndoableDeletes(
        scope = app.applicationScope,
        deleteRow = { app.database.memoryDao().deleteEverywhere(it, System.currentTimeMillis()) },
        restoreRow = { app.database.memoryDao().restore(it) },
        deleteOriginal = { app.originals.delete(it) },
        // A memory brought back lost its vector with the row: embed it again too.
        scheduleUpload = { EmbedWorker.schedule(app) },
    )

    /** The memory just deleted, that can still be brought back; null once the five seconds pass. */
    val pendingDelete: StateFlow<Memory?> = deletes.pending

    fun delete(memory: Memory) = deletes.delete(memory)

    fun undoDelete() = deletes.undo()

    /** Writes the person's note on a memory (null clears it), and sends it. */
    fun setNote(id: String, note: String?) {
        app.applicationScope.launch {
            if (app.database.captureDao().setNote(listOf(id), note) > 0) {
                // The note is part of what is embedded, and of what is read for actions.
                EmbedWorker.schedule(app)
                com.actum.act.ActWorker.schedule(app)
            }
        }
    }

    override fun onCleared() {
        // Gone mid-undo: make the delete final rather than leave a file behind.
        deletes.finish()
    }

    private companion object {
        const val MATCHES = 30
        const val NO_MODEL = "the search model is not on this phone"
    }
}
