package io.github.igormy.vocora.recorder.client

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

private const val EXTENSION = ".m4a"

/** A recording the user can play back. */
data class Recording(val name: String, val uri: Uri, val recordedAt: Long)

/**
 * Lists the recordings in the picked folder, newest first.
 *
 * It goes through the tree URI rather than the path: under scoped storage the app cannot read the
 * folder directly, even though the recorder writes to it.
 */
object RecordingsLibrary {

    fun list(context: Context): List<Recording> {
        val treeUri = RecordingsFolder.treeUri(context) ?: return emptyList()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )

        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )

        return runCatching {
            context.contentResolver.query(childrenUri, columns, null, null, null)?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(1) ?: continue
                        if (!name.endsWith(EXTENSION)) continue
                        add(
                            Recording(
                                name = name,
                                uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(0)),
                                recordedAt = cursor.getLong(2),
                            ),
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList()).sortedByDescending { it.recordedAt }
    }
}
