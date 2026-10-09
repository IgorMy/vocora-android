package io.github.igormy.vocora.recorder.logic

/** Who a call is with, as far as anything could tell before it ended. */
data class CallIdentity(val number: String?, val name: String?) {
    val isKnown: Boolean get() = !number.isNullOrBlank() || !name.isNullOrBlank()
}

/**
 * Decides whether a call belongs to someone on the do-not-record list.
 *
 * Both are tried because what is known about a call depends on where it was read from: the dialer's
 * notification carries a name for a saved contact and a bare number for anyone else, while the call
 * log always has the number.
 */
object BlacklistMatch {

    fun isBlocked(
        identity: CallIdentity,
        numbers: Collection<String>,
        names: Collection<String>,
    ): Boolean {
        val byNumber = identity.number?.let { PhoneNumbers.isListed(it, numbers) } == true
        if (byNumber) return true

        // A name only counts as a match against a name: "Lorena" is nobody's phone number.
        val callName = identity.name?.trim()?.lowercase() ?: return false
        if (callName.isEmpty()) return false
        return names.any { it.trim().lowercase() == callName }
    }
}
