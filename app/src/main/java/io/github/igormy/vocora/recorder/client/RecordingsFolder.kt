package io.github.igormy.vocora.recorder.client

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import io.github.igormy.vocora.recorder.logic.StoragePath

private const val PREFERENCES = "vocora"
private const val KEY_FOLDER = "recordings_folder"
private const val KEY_TREE_URI = "recordings_tree_uri"

/**
 * Remembers the folder the user picked for recordings, in two shapes.
 *
 * The recorder writes as the shell user, which cannot use the permission attached to the picked tree
 * URI, so it needs a plain path. The app is the opposite: it has the permission but no access to
 * that path under scoped storage, so to list and play recordings it needs the URI.
 */
object RecordingsFolder {

    fun path(context: Context): String? =
        preferences(context).getString(KEY_FOLDER, null)

    fun treeUri(context: Context): Uri? =
        preferences(context).getString(KEY_TREE_URI, null)?.let(Uri::parse)

    /**
     * Stores the folder behind [treeUri]. Returns false for volumes whose path cannot be resolved,
     * which is anything outside internal storage.
     */
    fun set(context: Context, treeUri: Uri): Boolean {
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return false
        val path = StoragePath.ofTreeDocumentId(documentId) ?: return false

        // Kept across restarts so the list and the player keep working without asking again.
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }

        preferences(context).edit()
            .putString(KEY_FOLDER, path)
            .putString(KEY_TREE_URI, treeUri.toString())
            .apply()
        return true
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
