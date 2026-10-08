package io.github.igormy.vocora.recorder.client

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

/** Who a number belongs to, as far as the address book knows. */
data class Contact(val name: String, val photoUri: Uri?)

/**
 * Puts a name and a face on a number, the way the dialer does.
 *
 * Without the contacts permission every lookup comes back empty, which is fine: the number alone is
 * still a usable label.
 */
object ContactLookup {

    private val COLUMNS = arrayOf(
        ContactsContract.PhoneLookup.DISPLAY_NAME,
        ContactsContract.PhoneLookup.PHOTO_URI,
    )

    /** Reads the number and name out of what the contact picker hands back. */
    fun pickedPhone(context: Context, pickedUri: Uri): Pair<String, String?>? = runCatching {
        val columns = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        )
        context.contentResolver.query(pickedUri, columns, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val number = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: return@use null
            number to cursor.getString(1)?.takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    fun of(context: Context, number: String): Contact? {
        if (number.isBlank()) return null
        // appendPath encodes the segment itself. Encoding it beforehand turned the + of an
        // international number into %252B, and the provider then matched nothing.
        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
            .buildUpon()
            .appendPath(number)
            .build()

        return runCatching {
            context.contentResolver.query(uri, COLUMNS, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val name = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: return@use null
                Contact(name, cursor.getString(1)?.let(Uri::parse))
            }
        }.getOrNull()
    }
}
