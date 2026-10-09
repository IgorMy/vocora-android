package io.github.igormy.vocora.recorder.server

import android.content.Context

private const val PREFERENCES = "vocora"
private const val KEY_URL = "server_url"
private const val KEY_TOKEN = "server_token"
private const val KEY_WIFI_ONLY = "server_wifi_only"
private const val KEY_SYNCED_AT = "server_synced_at"

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

    fun token(context: Context): String? =
        preferences(context).getString(KEY_TOKEN, null)?.trim()?.takeIf { it.isNotEmpty() }

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

    fun setSyncedAt(context: Context, at: String) =
        preferences(context).edit().putString(KEY_SYNCED_AT, at).apply()

    /** What the field shows, which is what was typed rather than what is usable. */
    fun typedUrl(context: Context): String = preferences(context).getString(KEY_URL, "").orEmpty()

    fun typedToken(context: Context): String = preferences(context).getString(KEY_TOKEN, "").orEmpty()

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
