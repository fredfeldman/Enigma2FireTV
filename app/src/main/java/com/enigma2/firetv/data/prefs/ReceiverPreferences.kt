package com.enigma2.firetv.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Thin wrapper around SharedPreferences for persisting Enigma2 receiver settings.
 */
class ReceiverPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var host: String
        get() = prefs.getString(KEY_HOST, "") ?: ""
        set(value) = prefs.edit { putString(KEY_HOST, value) }

    var port: Int
        get() = prefs.getInt(KEY_PORT, 80)
        set(value) = prefs.edit { putInt(KEY_PORT, value) }

    var useHttps: Boolean
        get() = prefs.getBoolean(KEY_HTTPS, false)
        set(value) = prefs.edit { putBoolean(KEY_HTTPS, value) }

    var username: String
        get() = prefs.getString(KEY_USER, "") ?: ""
        set(value) = prefs.edit { putString(KEY_USER, value) }

    var password: String
        get() = prefs.getString(KEY_PASS, "") ?: ""
        set(value) = prefs.edit { putString(KEY_PASS, value) }

    val isConfigured: Boolean
        get() = host.isNotBlank()

    fun streamUrl(serviceRef: String): String {
        val scheme = if (useHttps) "https" else "http"
        return "$scheme://$host:8001/$serviceRef"
    }

    fun piconUrl(piconPath: String): String {
        val scheme = if (useHttps) "https" else "http"
        return "$scheme://$host:$port$piconPath"
    }

    /**
     * Builds the HTTP stream URL for a local recording.
     * The receiver serves recording files via OpenWebif at /file?file=<path>.
     * Uses %20 encoding for spaces (not +) so the receiver path resolves correctly.
     */
    fun recordingStreamUrl(filename: String): String {
        val scheme = if (useHttps) "https" else "http"
        val encoded = android.net.Uri.encode(filename)
        return "$scheme://$host:$port/file?file=$encoded"
    }

    companion object {
        private const val PREFS_NAME = "enigma2_prefs"
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_HTTPS = "use_https"
        const val KEY_USER = "username"
        const val KEY_PASS = "password"
    }
}
