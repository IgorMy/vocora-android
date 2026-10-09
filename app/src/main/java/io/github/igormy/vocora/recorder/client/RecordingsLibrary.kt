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

/** A recording folder as the disk shows it, before the index adds what costs time to read. */
data class RecordingFolder(
    val name: String,
    val folderUri: Uri,
    val mixedUri: Uri,
    val recordedAt: Long,
)

/**
 * Looks at the recording folders on disk and deletes them.
 *
 * Everything goes through the tree URI rather than the path: under scoped storage the app cannot
 * touch the folder directly, even though the recorder writes to it.
 *
 * This is the slow half of listing recordings, which is why it is no longer what the list reads
 * from. Walking the folders is a query each, and measuring a recording means decoding its header;
 * the database keeps the answers so only new folders pay for it. Callers belong off the main thread.
 */
object RecordingsLibrary {

    /**
     * Every folder that holds a finished recording.
     *
     * A folder without its mix is skipped rather than reported empty: that is a call still being
     * recorded, and it shows up on a later look once the recorder has muxed it.
     */
    fun folders(context: Context): List<RecordingFolder> {
        val treeUri = RecordingsFolder.treeUri(context) ?: return emptyList()
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return emptyList()

        return children(context, treeUri, rootId)
            .filter { it.isDirectory }
            .mapNotNull { folder -> folderOf(context, treeUri, folder) }
    }

    /** Removes a recording, both sides and the mix with it. */
    fun delete(context: Context, recording: Recording): Boolean = runCatching {
        DocumentsContract.deleteDocument(context.contentResolver, recording.folderUri)
    }.getOrDefault(false)

    /** How long a recording lasts, read from the file because nothing else knows. */
    fun duration(context: Context, mixedUri: Uri): Int = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, mixedUri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toInt() ?: 0
        }
    }.getOrDefault(0)

    private fun folderOf(context: Context, treeUri: Uri, folder: Entry): RecordingFolder? {
        val mixed = children(context, treeUri, folder.documentId)
            .firstOrNull { it.name == MIXED_FILE }
            ?: return null

        return RecordingFolder(
            name = folder.name,
            folderUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, folder.documentId),
            mixedUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, mixed.documentId),
            // The name says when the call started; the folder's timestamp says when it was touched.
            recordedAt = RecordingLabel.startedAt(folder.name) ?: folder.lastModified,
        )
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
