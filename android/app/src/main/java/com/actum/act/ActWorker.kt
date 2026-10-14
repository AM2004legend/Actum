package com.actum.act

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.actum.ActumApp
import com.actum.data.Memory
import com.actum.data.MemoryType
import com.actum.embed.EmbedText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Reads recently saved items for what they ask to be done, one at a time and
 * newest first, so **Find things to do in this** has its cards at once. Gives
 * way to the person: when they ask for anything, it stops and tries later.
 *
 * Only the last [DAYS] days' items, at most [PER_PASS] a pass: each takes the
 * model seconds and warms the phone, and an old item is rarely asked about.
 * Those are read when they are asked about, and kept then.
 */
class ActWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as ActumApp
        return passes.withLock {
            var asked: Long
            do {
                asked = requests.get()
                val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(DAYS)
                val waiting = app.database.memoryDao().getAll()
                    .filter { it.capturedAt >= since && readable(it) }
                    .sortedByDescending { it.capturedAt }
                    .take(PER_PASS)
                for (memory in waiting) {
                    // Not now: the person is asking for something, or there is no model. Later.
                    if (!app.actor.prepare(memory.id, EmbedText.text(memory))) return@withLock Result.retry()
                }
                // Saved or read again while this ran: one more pass.
            } while (requests.get() != asked)
            Result.success()
        }
    }

    /** Words to read, and all of them there: a picture or PDF is read for its text first. */
    private fun readable(memory: Memory): Boolean {
        if (memory.type == MemoryType.AUDIO) return false
        if (memory.extractedText == null && (memory.type == MemoryType.IMAGE || memory.type == MemoryType.PDF)) return false
        return EmbedText.text(memory).split(Regex("\\s+")).count { it.isNotEmpty() } >= MIN_WORDS
    }

    companion object {
        private const val NAME = "prepare-actions"
        private const val DAYS = 7L
        private const val PER_PASS = 10
        private const val MIN_WORDS = 3

        private val requests = AtomicLong()
        private val passes = Mutex()

        /** Something was saved, or its text read: called with the embedding schedule. */
        fun schedule(context: Context) {
            requests.incrementAndGet()
            val request = OneTimeWorkRequestBuilder<ActWorker>()
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
