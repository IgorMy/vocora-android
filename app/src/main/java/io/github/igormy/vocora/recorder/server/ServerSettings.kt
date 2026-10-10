package io.github.igormy.vocora.recorder.server

import android.content.Context

private const val PREFERENCES = "vocora"
private const val KEY_URL = "server_url"
private const val KEY_TOKEN = "server_token"
private const val KEY_API_VERSION = "server_api_version"
private const val KEY_WIFI_ONLY = "server_wifi_only"
private const val KEY_AUTO_SYNC = "server_auto_sync"
private const val KEY_SYNCED_AT = "server_synced_at"
private const val KEY_SYNCED_URL = "server_synced_url"

/** The server's recordings live under the major version it runs, and today that is this one. */
private const val DEFAULT_API_VERSION = "v0"

/**
 * Where the server is, how to prove who is asking, and over which network.
 *
 * Both the address and the token have to be there for anything to be sent: a queue that cannot
 * authenticate would only pile up failures, so it does not run at all until [isConfigured].
 */
object ServerSettings {

    /** The address, without the trailing slash, so paths can be appended without thinking. */
    fun url(context: Context): String? =
        preferences(context).getString(KEY_URL, null)?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }

    /**
     * Which API the recordings are asked for, as a path segment.
     *
     * The server puts them under the major version it runs, `/v0` for a 0.x server, and only moves
     * them on a breaking release. Nothing announces it, so it is told rather than discovered, and a
     * bare number is taken to mean the same thing as the same number with its v.
     */
    fun apiVersion(context: Context): String {
        val typed = preferences(context).getString(KEY_API_VERSION, null)
            ?.trim()?.trim('/')?.takeIf { it.isNotEmpty() }
            ?: return DEFAULT_API_VERSION
        return if (typed.first().isDigit()) "v$typed" else typed
    }

    fun setApiVersion(context: Context, version: String) =
        preferences(context).edit().putString(KEY_API_VERSION, version).apply()

    fun typedApiVersion(context: Context): String =
        preferences(context).getString(KEY_API_VERSION, DEFAULT_API_VERSION).orEmpty()

    fun token(context: Context): String? =
        preferences(context).getString(KEY_TOKEN, null)?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Whether the app keeps itself in step with the server without being asked.
     *
     * On, it checks the whole listing when it opens and queues whatever the server turns out not to
     * have, and it queues each call as it ends. Off, nothing goes anywhere until it is asked to,
     * recording by recording.
     *
     * Off by default. Sending recordings somewhere is not something to start doing on its own.
     */
    fun autoSync(context: Context): Boolean =
        preferences(context).getBoolean(KEY_AUTO_SYNC, false)

    fun setAutoSync(context: Context, autoSync: Boolean) =
        preferences(context).edit().putBoolean(KEY_AUTO_SYNC, autoSync).apply()

    /** Three files of a ten minute call are around 15 MB, so mobile data is opt in. */
    fun wifiOnly(context: Context): Boolean =
        preferences(context).getBoolean(KEY_WIFI_ONLY, true)

    fun isConfigured(context: Context): Boolean =
        url(context) != null && token(context) != null

    fun setUrl(context: Context, url: String) =
        preferences(context).edit().putString(KEY_URL, url).apply()

    fun setToken(context: Context, token: String) =
        preferences(context).edit().putString(KEY_TOKEN, token).apply()

    fun setWifiOnly(context: Context, wifiOnly: Boolean) =
        preferences(context).edit().putBoolean(KEY_WIFI_ONLY, wifiOnly).apply()

    /**
     * The last change the server reported, so the next sync only asks for what happened after it.
     *
     * The server's own clock, kept as it was written: comparing it against this phone's would only
     * bring two clocks into a question that needs one.
     */
    fun syncedAt(context: Context): String? = preferences(context).getString(KEY_SYNCED_AT, null)

    fun setSyncedAt(context: Context, at: String?) =
        preferences(context).edit().putString(KEY_SYNCED_AT, at).apply()

    /** Which server the stored answers came from, so a change of address can be noticed. */
    fun syncedUrl(context: Context): String? = preferences(context).getString(KEY_SYNCED_URL, null)

    fun setSyncedUrl(context: Context, url: String) =
        preferences(context).edit().putString(KEY_SYNCED_URL, url).apply()

    /** What the field shows, which is what was typed rather than what is usable. */
    fun typedUrl(context: Context): String = preferences(context).getString(KEY_URL, "").orEmpty()

    fun typedToken(context: Context): String = preferences(context).getString(KEY_TOKEN, "").orEmpty()

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
