package com.actum.open

import android.content.Context
import com.actum.ActumApp
import com.actum.data.Memory
import com.actum.data.OriginalStore
import java.io.File

/**
 * Entry points for opening and sharing originals from memory detail.
 *
 * Called by Suparno's detail screen (U5).
 */
object Originals {

    /** Decides the action for [memory] and stored [file]. */
    fun actionFor(memory: Memory, file: File?): OriginalAction =
        OriginalAction.decide(memory, file)

    /** Decides the action for [memory], resolving its file through [store]. */
    fun actionFor(
        context: Context,
        memory: Memory,
        store: OriginalStore = (context.applicationContext as? ActumApp)?.originals ?: OriginalStore(context),
    ): OriginalAction =
        OriginalAction.decide(memory, store.fileFor(memory))

    /** Decides the onward share for [memory] and stored [file], or null when nothing to share. */
    fun shareFor(memory: Memory, file: File?): ShareOut? =
        ShareOut.decide(memory, file)

    /** Decides the onward share for [memory], resolving its file through [store], or null. */
    fun shareFor(
        context: Context,
        memory: Memory,
        store: OriginalStore = (context.applicationContext as? ActumApp)?.originals ?: OriginalStore(context),
    ): ShareOut? =
        ShareOut.decide(memory, store.fileFor(memory))

    /** Opens or copies the original for [memory]. */
    fun open(
        context: Context,
        memory: Memory,
        store: OriginalStore = (context.applicationContext as? ActumApp)?.originals ?: OriginalStore(context),
    ) {
        val action = actionFor(context, memory, store)
        action.open(context)
    }

    /** Shares the memory onward using the system share chooser. */
    fun share(
        context: Context,
        memory: Memory,
        store: OriginalStore = (context.applicationContext as? ActumApp)?.originals ?: OriginalStore(context),
    ) {
        val share = shareFor(context, memory, store)
        share?.share(context)
    }
}
