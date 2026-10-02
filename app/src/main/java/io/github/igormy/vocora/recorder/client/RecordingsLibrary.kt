package io.github.igormy.vocora.recorder.client

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/** The file inside a recording folder that holds both sides mixed, which is what gets played. */
private const val MIXED_FILE = "mixed.m4a"

private val COLUMNS = arrayOf(
    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    DocumentsContract.Document.COLUMN_MIME_TYPE,
)

/** A recorded call: one folder holding the mix and each side on its own. */
data class Recording(val name: String, val mixedUri: Uri, val recordedAt: Long)

/**
 * Lists the recorded calls in the picked folder, newest first.
 *
 * It goes through the tree URI rather than the path: under scoped storage the app cannot read the
 * folder directly, even though the recorder writes to it.
 */
object RecordingsLibrary {

    fun list(context: Context): List<Recording> {
        val treeUri = RecordingsFolder.treeUri(context) ?: return emptyList()
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return emptyList()

        return children(context, treeUri, rootId)
            .filter { it.isDirectory }
            .mapNotNull { folder ->
                val mixed = children(context, treeUri, folder.documentId)
                    .firstOrNull { it.name == MIXED_FILE }
                    ?: return@mapNotNull null
                Recording(
                    name = folder.name,
                    mixedUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, mixed.documentId),
                    recordedAt = folder.lastModified,
                )
            }
            .sortedByDescending { it.recordedAt }
    }

    private data class Entry(
        val documentId: String,
        val name: String,
        val lastModified: Long,
        val isDirectory: Boolean,
    )

    private fun children(context: Context, treeUri: Uri, parentDocumentId: String): List<Entry> {
        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        return runCatching {
            context.contentResolver.query(childrenUri, COLUMNS, null, null, null)?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            Entry(
                                documentId = cursor.getString(0),
                                name = cursor.getString(1).orEmpty(),
                                lastModified = cursor.getLong(2),
                                isDirectory = cursor.getString(3) ==
                                    DocumentsContract.Document.MIME_TYPE_DIR,
                            ),
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }
}
