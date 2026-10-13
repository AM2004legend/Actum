package com.actum

import android.app.Application
import com.actum.data.ActumDatabase
import com.actum.data.OriginalStore
import com.actum.embed.EmbedWorker
import com.actum.embed.Embedder
import com.actum.embed.ModelStore
import com.actum.imports.BulkImporter
import com.actum.ocr.OcrQueue
import com.actum.ui.theme.Look
import com.actum.ocr.uploadScheduler
import com.actum.act.Actor
import com.actum.act.Executor
import com.actum.data.Diary
import com.actum.data.DiaryVault
import com.actum.model.GemmaModel
import com.actum.model.GemmaRouter
import com.actum.ui.gate.ModelGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class ActumApp : Application() {

    /**
     * Work that must outlive the Activity that started it.
     *
     * Capture surfaces finish within milliseconds of launching -- that is the
     * point of them -- so a `lifecycleScope` coroutine would be cancelled
     * before the insert ever reached Room. SupervisorJob so one failed save
     * cannot take the scope down with it.
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The one database. Built on first use, never on the main thread at a cold start. */
    val database: ActumDatabase get() = ActumDatabase.get(this)

    /** Where the originals of saved pictures and PDFs live (architecture rule 1). */
    val originals: OriginalStore by lazy { OriginalStore(this) }

    /** The embedding model's file, sideloaded with adb. */
    val models: ModelStore by lazy { ModelStore(this) }

    /** EmbeddingGemma 2 on the phone: memories are embedded, and searched, here (local-first L1). */
    val embedder: Embedder by lazy { Embedder(models::ready, cacheDir, applicationScope) }

    /** Bulk importer for bringing in content already on the phone (reading R5). */
    val importer: BulkImporter by lazy { BulkImporter(this) }

    /**
     * Reading workstream: true while the OCR queue is working through unread
     * pictures and PDFs, so the uploader holds them back rather than sending
     * them unread. Presumed true until the queue has looked, and goes false in
     * a finally when the queue finishes or never starts.
     */
    val readingOriginals = MutableStateFlow(true)

    /**
     * Capture workstream: memory ids that must not be uploaded yet. The capture
     * sheet adds its ids here while the note field is open; the sync
     * workstream's queue skips them. Released on dismiss, on leaving for
     * another app, and in onDestroy.
     */
    val uploadHolds: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /** Gemma on the phone: the one model, loaded when first asked. */
    val router: GemmaRouter by lazy { GemmaRouter(cacheDir) }

    /** Milestone M1's measurement, here so a run outlives a rotation. */
    val modelGate: ModelGate by lazy { ModelGate(GemmaModel(this), router, applicationScope) }

    /** The phone's own key, under the diary's password and for entries written before one is set. */
    val diaryKeystore: Diary by lazy { Diary() }

    /** The diary's password lock. Its few values sit in a preferences file; the password is in none of them. */
    val diaryVault: DiaryVault by lazy {
        val prefs = getSharedPreferences("diary_vault", MODE_PRIVATE)
        DiaryVault(
            store = object : DiaryVault.Store {
                override fun get(key: String): String? = prefs.getString(key, null)
                // commit, not apply: a password change must be on disk before it is called done.
                override fun put(key: String, value: String) {
                    prefs.edit().putString(key, value).commit()
                }
            },
            device = diaryKeystore,
        )
    }

    /** A request to do something, from the person's words to the confirmed action. */
    val actor: Actor by lazy { Actor(GemmaModel(this), router, Executor(database.listsDao(), diaryVault, diaryKeystore), applicationScope) }

    override fun onCreate() {
        super.onCreate()
        // Before anything draws: light or dark, as the person last left it.
        Look.load(this)
        // What is saved or read is embedded on the phone, for search by meaning.
        // Nothing is uploaded: Actum has no server and no network.
        uploadScheduler = { EmbedWorker.schedule(it) }
        // Inside applicationScope so none of it touches the main thread during
        // a capture's cold start. Building the queue builds the database.
        OcrQueue.launchIn(applicationScope, readingOriginals) { OcrQueue(this) }
        applicationScope.launch { EmbedWorker.schedule(this@ActumApp) }
    }
}
