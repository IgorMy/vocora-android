package io.github.igormy.vocora.recorder.client

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import io.github.igormy.vocora.recorder.logic.RecordingDay
import io.github.igormy.vocora.recorder.logic.RecordingLabel

/** The file inside a recording folder that holds both sides mixed, which is what gets played. */
private const val MIXED_FILE = "mixed.m4a"

private val COLUMNS = arrayOf(
    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    DocumentsContract.Document.COLUMN_MIME_TYPE,
)

/** A recorded call: one folder holding the mix and each side on its own. */
data class Recording(
    val name: String,
    val folderUri: Uri,
    val mixedUri: Uri,
    val recordedAt: Long,
    val durationMillis: Int,
    val number: String?,
    val contact: Contact?,
) {
    /** Midnight of the day it happened, which is what the list groups by. */
    val day: Long get() = RecordingDay.startOfDay(recordedAt)
}

/**
 * Lists and deletes the recorded calls in the picked folder.
 *
 * Everything goes through the tree URI rather than the path: under scoped storage the app cannot
 * touch the folder directly, even though the recorder writes to it.
 *
 * Reading durations and looking up contacts means a few queries per recording, so callers should
 * stay off the main thread.
 */
object RecordingsLibrary {

    fun list(context: Context): List<Recording> {
        val treeUri = RecordingsFolder.treeUri(context) ?: return emptyList()
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return emptyList()

        return children(context, treeUri, rootId)
            .filter { it.isDirectory }
            .mapNotNull { folder -> recordingOf(context, treeUri, folder) }
            .sortedByDescending { it.recordedAt }
    }

    /** Removes a recording, both sides and the mix with it. */
    fun delete(context: Context, recording: Recording): Boolean = runCatching {
        DocumentsContract.deleteDocument(context.contentResolver, recording.folderUri)
    }.getOrDefault(false)

    private fun recordingOf(context: Context, treeUri: Uri, folder: Entry): Recording? {
        val mixed = children(context, treeUri, folder.documentId)
            .firstOrNull { it.name == MIXED_FILE }
            ?: return null

        val mixedUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, mixed.documentId)
        val number = RecordingLabel.number(folder.name)

        return Recording(
            name = folder.name,
            folderUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, folder.documentId),
            mixedUri = mixedUri,
            // The name says when the call started; the folder's timestamp says when it was touched.
            recordedAt = RecordingLabel.startedAt(folder.name) ?: folder.lastModified,
            durationMillis = durationOf(context, mixedUri),
            number = number,
            contact = number?.let { ContactLookup.of(context, it) },
        )
    }

    private fun durationOf(context: Context, uri: Uri): Int = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toInt() ?: 0
        }
    }.getOrDefault(0)

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
