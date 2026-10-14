package com.actum.model

import android.content.Context
import com.actum.imports.Sha256
import java.io.File

/**
 * Where the Gemma model lives on the phone. Never in the APK and never
 * downloaded: Actum has no network, so the file is sideloaded with adb into
 * the app's files directory and used only when its SHA-256 is the published one.
 *
 * Actum knows a few published models ([Kind]); one is chosen at a time, and
 * the choice is kept on the phone. Gemma 4 E2B unless another was chosen.
 */
class GemmaModel(private val context: Context) {

    /** A published model file Actum can run, pinned by size and hash. */
    enum class Kind(
        val id: String,
        val label: String,
        val fileName: String,
        val size: Long,
        val sha256: String,
        val prompt: GemmaRouter.Prompt,
    ) {
        /** litert-community/gemma-4-E2B-it-litert-lm at b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1. */
        GEMMA_4_E2B(
            "gemma4-e2b", "Gemma 4 E2B", "gemma-4-E2B-it.litertlm", 2_588_147_712L,
            "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
            GemmaRouter.Prompt.SYSTEM,
        ),

        /** litert-community/Gemma3-1B-IT at d448835d84411a08f577e25f3afebe528402d73b. */
        GEMMA_3_1B(
            "gemma3-1b", "Gemma 3 1B", "gemma3-1b-it-int4.litertlm", 584_417_280L,
            "1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be",
            GemmaRouter.Prompt.TURNS,
        ),

        /** litert-community/gemma-3-270m-it at 9d2093270fb5aa49a986b49b5779d763dde7b630. */
        GEMMA_3_270M(
            "gemma3-270m", "Gemma 3 270M", "gemma3-270m-it-q8.litertlm", 304_005_120L,
            "757e9119fa5bd667a2774fb470ac4afcd3190a21c677f8e69a5d6bc908abdd63",
            GemmaRouter.Prompt.TURNS,
        ),
        ;

        companion object {
            fun byId(id: String?): Kind? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
        }
    }

    sealed interface State {
        /** No file at either of [places]. */
        data class Missing(val kind: Kind, val places: List<String>) : State

        /** A file, but not the published one: a partial push, or another model. */
        data class Wrong(val kind: Kind, val file: File, val why: String) : State

        data class Ready(val kind: Kind, val file: File) : State
    }

    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The model Actum reads with. */
    val chosen: Kind get() = Kind.byId(prefs.getString(KEY_CHOSEN, null)) ?: Kind.GEMMA_4_E2B

    /** Kept on disk at once: the next [check], from any screen, finds it. */
    fun choose(kind: Kind) {
        prefs.edit().putString(KEY_CHOSEN, kind.id).commit()
    }

    private fun places(kind: Kind): List<File> = listOfNotNull(
        File(File(context.filesDir, "models"), kind.fileName),
        context.getExternalFilesDir(null)?.let { File(it, kind.fileName) },
    )

    /** Hashes the file the first time (seconds to tens of seconds), so never on the main thread. */
    fun check(kind: Kind = chosen): State {
        val places = places(kind)
        val file = places.firstOrNull { it.isFile } ?: return State.Missing(kind, places.map { it.path })
        if (file.length() != kind.size) return State.Wrong(kind, file, "is ${file.length()} bytes, expected ${kind.size}")
        // Hashed once per file: the mark names the size and time it was hashed at.
        val mark = File(context.filesDir, "${kind.fileName}.verified")
        val stamp = "${file.path}:${file.length()}:${file.lastModified()}"
        if (mark.isFile && mark.readText() == stamp) return State.Ready(kind, file)
        val hash = Sha256.digest(file)
        if (hash != kind.sha256) return State.Wrong(kind, file, "SHA-256 is $hash, expected ${kind.sha256}")
        mark.writeText(stamp)
        return State.Ready(kind, file)
    }

    private companion object {
        const val PREFS = "model"
        const val KEY_CHOSEN = "chosen"
    }
}
