package com.actum.embed

import android.content.Context
import java.io.File

/**
 * Where the embedding model lives on the phone. Never in the APK and never
 * downloaded: `adb push` puts it in the app's external files directory, and
 * a file of the published size is used as it is.
 */
class ModelStore(private val context: Context) {

    val file: File get() = File(File(context.filesDir, "models"), FILE_NAME)

    private val pushed: File? get() = context.getExternalFilesDir(null)?.let { File(it, FILE_NAME) }

    /** The model, when a whole one is on the phone. */
    fun ready(): File? = listOfNotNull(file, pushed).firstOrNull { it.isFile && it.length() == SIZE }

    companion object {
        const val FILE_NAME = "${EmbedText.MODEL}.litertlm"

        /** From litert-community/embeddinggemma-2-text-vision-440m-litert-lm at e301f74d (Apache 2.0). */
        const val SIZE = 387_710_976L
    }
}
