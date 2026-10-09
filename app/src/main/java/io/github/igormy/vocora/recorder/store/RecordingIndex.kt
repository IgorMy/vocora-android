package io.github.igormy.vocora.recorder.store

import android.content.Context
import android.net.Uri
import io.github.igormy.vocora.recorder.client.Contact
import io.github.igormy.vocora.recorder.client.ContactLookup
import io.github.igormy.vocora.recorder.client.Recording
import io.github.igormy.vocora.recorder.client.RecordingFolder
import io.github.igormy.vocora.recorder.client.RecordingsFolder
import io.github.igormy.vocora.recorder.client.RecordingsLibrary
import io.github.igormy.vocora.recorder.logic.RecordingLabel
import io.github.igormy.vocora.recorder.logic.RecordingProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * What has been recorded, answered from the database and kept honest against the disk.
 *
 * The list used to be built by walking the folder and opening every file, which is why a cold start
 * sat on "Reading your recordings…". Now it comes out of the table at once and the disk is only read
 * to reconcile, where the rule is the one agreed: a folder with no row gets one, and a row whose
 * folder is gone is deleted.
 *
 * Renames take care of themselves through that same rule. The recorder names a folder after the call
 * log once the call ends, so a row written while it was still called by the start time is dropped and
 * the renamed folder gets a row of its own.
 */
object RecordingIndex {

    /**
     * Names and faces for numbers, remembered for as long as the process lives.
     *
     * Resolving a contact is a query, and the list is re-read whenever anything changes; the address
     * book does not change often enough to pay that each time. [forgetContacts] is there for when it
     * does, which in practice means the moment the permission is granted.
     */
    private val contacts = mutableMapOf<String, Contact?>()

    /** The list, kept up to date by the database itself. */
    fun stream(context: Context): Flow<List<Recording>> =
        VocoraDatabase.of(context).recordings().stream()
            .map { rows -> rows.map { it.asRecording(context) } }
            .flowOn(Dispatchers.IO)

    /** The list read once, for when something outside the database changed what it shows. */
    suspend fun read(context: Context): List<Recording> = withContext(Dispatchers.IO) {
        VocoraDatabase.of(context).recordings().all().map { it.asRecording(context) }
    }

    /** Makes the table agree with the disk. */
    suspend fun reconcile(context: Context) = withContext(Dispatchers.IO) {
        // Without a folder there is nothing to compare against, and emptying the table because the
        // app cannot see the disk would throw away the index for no reason.
        if (RecordingsFolder.treeUri(context) == null) return@withContext

        val dao = VocoraDatabase.of(context).recordings()
        val onDisk = RecordingsLibrary.folders(context).associateBy { it.name }
        val known = dao.folders().toSet()

        (known - onDisk.keys).takeIf { it.isNotEmpty() }?.let { dao.forget(it.toList()) }
        // Only new folders are measured, which is what makes reconciliation cheap once settled.
        (onDisk.keys - known).forEach { name -> dao.put(entityOf(context, onDisk.getValue(name))) }
    }

    /** Removes a recording from the disk and from the index, in that order. */
    suspend fun delete(context: Context, recording: Recording) = withContext(Dispatchers.IO) {
        RecordingsLibrary.delete(context, recording)
        VocoraDatabase.of(context).recordings().forget(listOf(recording.name))
    }

    /** Drops the remembered contacts so the next read asks the address book again. */
    fun forgetContacts() = synchronized(contacts) { contacts.clear() }

    private fun entityOf(context: Context, folder: RecordingFolder) = RecordingEntity(
        folder = folder.name,
        recordedAt = folder.recordedAt,
        number = RecordingLabel.number(folder.name),
        direction = RecordingLabel.direction(folder.name),
        durationMillis = RecordingsLibrary.duration(context, folder.mixedUri),
        folderUri = folder.folderUri.toString(),
        mixedUri = folder.mixedUri.toString(),
    )

    private fun RecordingEntity.asRecording(context: Context) = Recording(
        name = folder,
        folderUri = Uri.parse(folderUri),
        mixedUri = Uri.parse(mixedUri),
        recordedAt = recordedAt,
        durationMillis = durationMillis,
        number = number,
        contact = number?.let { contactOf(context, it) },
        progress = RecordingProgress.of(uploadState, serverStatus),
    )

    private fun contactOf(context: Context, number: String): Contact? = synchronized(contacts) {
        // A number that belongs to nobody is worth remembering too, so the key is what is checked.
        if (contacts.containsKey(number)) return@synchronized contacts[number]
        ContactLookup.of(context, number).also { contacts[number] = it }
    }
}
