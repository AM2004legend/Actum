package com.actum.model

import android.content.Context
import com.actum.imports.Sha256
import java.io.File

/**
 * Where the Gemma model lives on the phone. Never in the APK and never
 * downloaded: Actum has no network, so the file is sideloaded with adb into
 * the app's files directory and used only when its SHA-256 is the published one.
 */
class GemmaModel(private val context: Context) {

    sealed interface State {
        /** No file at either of [places]. */
        data class Missing(val places: List<String>) : State

        /** A file, but not the published one: a partial push, or another model. */
        data class Wrong(val file: File, val why: String) : State

        data class Ready(val file: File) : State
    }

    private val places: List<File>
        get() = listOfNotNull(
            File(File(context.filesDir, "models"), FILE_NAME),
            context.getExternalFilesDir(null)?.let { File(it, FILE_NAME) },
        )

    /** Hashes 2.6 GB the first time (tens of seconds), so never on the main thread. */
    fun check(): State {
        val file = places.firstOrNull { it.isFile } ?: return State.Missing(places.map { it.path })
        if (file.length() != SIZE) return State.Wrong(file, "is ${file.length()} bytes, expected $SIZE")
        // Hashed once per file: the mark names the size and time it was hashed at.
        val mark = File(context.filesDir, "$FILE_NAME.verified")
        val stamp = "${file.path}:${file.length()}:${file.lastModified()}"
        if (mark.isFile && mark.readText() == stamp) return State.Ready(file)
        val hash = Sha256.digest(file)
        if (hash != SHA256) return State.Wrong(file, "SHA-256 is $hash, expected $SHA256")
        mark.writeText(stamp)
        return State.Ready(file)
    }

    companion object {
        /**
         * Gemma 4 E2B, from litert-community/gemma-4-E2B-it-litert-lm (Apache 2.0)
         * at revision b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1.
         */
        const val FILE_NAME = "gemma-4-E2B-it.litertlm"
        const val SIZE = 2_588_147_712L
        const val SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
    }
}
