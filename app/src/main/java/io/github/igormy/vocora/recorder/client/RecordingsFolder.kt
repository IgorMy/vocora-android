package io.github.igormy.vocora.recorder.client

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import io.github.igormy.vocora.recorder.logic.StoragePath

private const val PREFERENCES = "vocora"
private const val KEY_FOLDER = "recordings_folder"

/** Remembers the folder the user picked for recordings. */
object RecordingsFolder {

    fun get(context: Context): String? =
        preferences(context).getString(KEY_FOLDER, null)

    /**
     * Stores the folder behind [treeUri]. Returns false for volumes whose path cannot be resolved,
     * which is anything outside internal storage.
     */
    fun set(context: Context, treeUri: Uri): Boolean {
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return false
        val path = StoragePath.ofTreeDocumentId(documentId) ?: return false
        preferences(context).edit().putString(KEY_FOLDER, path).apply()
        return true
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
