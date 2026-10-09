package io.github.igormy.vocora.recorder.client

import android.content.Context

private const val PREFERENCES = "vocora"
private const val KEY_BLACKLIST = "blacklist"

/** Entries are kept as `number|label`, the label being whoever the number was added as. */
private const val SEPARATOR = "|"

/**
 * Someone whose calls are not kept.
 *
 * [contact] is looked up rather than stored: a name or a photo that changed in the address book
 * should show as it is now, not as it was when the number was added.
 */
data class BlacklistEntry(
    val number: String,
    val label: String?,
    val contact: Contact? = null,
) {
    val shown: String get() = contact?.name ?: label ?: number
}

/** Numbers whose calls Vocora records and then throws away. */
object Blacklist {

    /** Entries with their contact resolved, which queries the address book once per number. */
    fun entriesWithContacts(context: Context): List<BlacklistEntry> =
        entries(context)
            .map { it.copy(contact = ContactLookup.of(context, it.number)) }
            .sortedBy { it.shown.lowercase() }

    fun entries(context: Context): List<BlacklistEntry> =
        preferences(context).getStringSet(KEY_BLACKLIST, emptySet())
            .orEmpty()
            .map { stored ->
                val parts = stored.split(SEPARATOR, limit = 2)
                BlacklistEntry(parts[0], parts.getOrNull(1)?.takeIf { it.isNotBlank() })
            }
            .sortedBy { it.shown.lowercase() }

    fun numbers(context: Context): List<String> = entries(context).map { it.number }

    /** The names their contacts go by, for matching a call the dialer only named. */
    fun names(context: Context): List<String> =
        entriesWithContacts(context).mapNotNull { it.contact?.name ?: it.label }

    fun add(context: Context, number: String, label: String?) {
        val cleaned = number.trim()
        if (cleaned.isEmpty()) return
        save(context, entries(context).filterNot { it.number == cleaned } + BlacklistEntry(cleaned, label))
    }

    fun remove(context: Context, entry: BlacklistEntry) {
        save(context, entries(context).filterNot { it.number == entry.number })
    }

    private fun save(context: Context, entries: List<BlacklistEntry>) {
        val stored = entries.map { "${it.number}$SEPARATOR${it.label.orEmpty()}" }.toSet()
        preferences(context).edit().putStringSet(KEY_BLACKLIST, stored).apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
