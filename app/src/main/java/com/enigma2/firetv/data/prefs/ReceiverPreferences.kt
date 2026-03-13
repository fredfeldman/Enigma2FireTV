package com.enigma2.firetv.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.enigma2.firetv.data.model.DeviceProfile
import com.enigma2.firetv.data.model.Service
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Persists Enigma2 receiver settings. Supports multiple named device profiles.
 * Legacy single-device keys (host, port, etc.) are kept in sync with the active
 * profile so that existing code (SettingsFragment, ApiClient callers) continues
 * to work without changes.
 */
class ReceiverPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val gson = Gson()

    init {
        migrateIfNeeded()
    }

    // ── Device list ───────────────────────────────────────────────────────

    var devices: List<DeviceProfile>
        get() {
            val json = prefs.getString(KEY_DEVICE_PROFILES, null) ?: return emptyList()
            return try {
                val type = object : TypeToken<List<DeviceProfile>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
        private set(value) = prefs.edit { putString(KEY_DEVICE_PROFILES, gson.toJson(value)) }

    var activeDeviceId: String
        get() = prefs.getString(KEY_ACTIVE_DEVICE_ID, "") ?: ""
        private set(value) = prefs.edit { putString(KEY_ACTIVE_DEVICE_ID, value) }

    val activeDevice: DeviceProfile?
        get() {
            val id = activeDeviceId
            return if (id.isNotBlank()) devices.firstOrNull { it.id == id }
                   else devices.firstOrNull()
        }

    fun addDevice(profile: DeviceProfile) {
        devices = devices + profile
        setActiveDevice(profile.id)
    }

    fun updateDevice(profile: DeviceProfile) {
        devices = devices.map { if (it.id == profile.id) profile else it }
        if (profile.id == activeDeviceId) {
            prefs.edit {
                putString(KEY_HOST, profile.host)
                putInt(KEY_PORT, profile.port)
                putBoolean(KEY_HTTPS, profile.useHttps)
                putString(KEY_USER, profile.username)
                putString(KEY_PASS, profile.password)
            }
        }
    }

    fun removeDevice(id: String) {
        devices = devices.filter { it.id != id }
        if (activeDeviceId == id) {
            val next = devices.firstOrNull()
            if (next != null) setActiveDevice(next.id)
            else prefs.edit { putString(KEY_ACTIVE_DEVICE_ID, "") }
        }
    }

    /** Switches the active device and syncs its settings into the legacy keys. */
    fun setActiveDevice(id: String) {
        prefs.edit { putString(KEY_ACTIVE_DEVICE_ID, id) }
        val device = devices.firstOrNull { it.id == id } ?: return
        prefs.edit {
            putString(KEY_HOST, device.host)
            putInt(KEY_PORT, device.port)
            putBoolean(KEY_HTTPS, device.useHttps)
            putString(KEY_USER, device.username)
            putString(KEY_PASS, device.password)
        }
    }

    // ── Active-device convenience properties ─────────────────────────────
    // Getters read from the legacy keys (always in sync with the active profile).
    // Setters write to legacy keys then sync the change back into the active profile.

    var host: String
        get() = prefs.getString(KEY_HOST, "") ?: ""
        set(value) {
            prefs.edit { putString(KEY_HOST, value) }
            syncActiveDevice()
        }

    var port: Int
        get() = prefs.getInt(KEY_PORT, 80)
        set(value) {
            prefs.edit { putInt(KEY_PORT, value) }
            syncActiveDevice()
        }

    var useHttps: Boolean
        get() = prefs.getBoolean(KEY_HTTPS, false)
        set(value) {
            prefs.edit { putBoolean(KEY_HTTPS, value) }
            syncActiveDevice()
        }

    var username: String
        get() = prefs.getString(KEY_USER, "") ?: ""
        set(value) {
            prefs.edit { putString(KEY_USER, value) }
            syncActiveDevice()
        }

    var password: String
        get() = prefs.getString(KEY_PASS, "") ?: ""
        set(value) {
            prefs.edit { putString(KEY_PASS, value) }
            syncActiveDevice()
        }

    /** Updates the active DeviceProfile in the list to match the current legacy key values. */
    private fun syncActiveDevice() {
        val active = activeDevice ?: return
        val updated = active.copy(
            host = host, port = port, useHttps = useHttps,
            username = username, password = password
        )
        devices = devices.map { if (it.id == active.id) updated else it }
    }

    // ── Bouquet filter ────────────────────────────────────────────────────

    var hiddenBouquetRefs: Set<String>
        get() = prefs.getStringSet(KEY_HIDDEN_BOUQUETS, emptySet()) ?: emptySet()
        set(value) = prefs.edit { putStringSet(KEY_HIDDEN_BOUQUETS, value) }

    // ── Favorites ─────────────────────────────────────────────────────────

    /** Returns all favorite services in insertion order. */
    var favoriteServices: List<Service>
        get() {
            return try {
                val json = prefs.getString(KEY_FAVORITES, null) ?: return emptyList()
                val type = object : TypeToken<List<Service>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                // Handles ClassCastException if old StringSet data is present, or JSON errors
                prefs.edit { remove(KEY_FAVORITES) }
                emptyList()
            }
        }
        private set(value) = prefs.edit { putString(KEY_FAVORITES, gson.toJson(value)) }

    val favoriteServiceRefs: Set<String>
        get() = favoriteServices.map { it.ref }.toSet()

    fun toggleFavorite(service: Service) {
        val current = favoriteServices.toMutableList()
        val idx = current.indexOfFirst { it.ref == service.ref }
        if (idx >= 0) current.removeAt(idx) else current.add(service)
        favoriteServices = current
    }

    fun isFavorite(serviceRef: String) = favoriteServices.any { it.ref == serviceRef }

    // ── Playback position resume ───────────────────────────────────────────

    fun getPlaybackPosition(streamUrl: String): Long {
        return prefs.getLong(positionKey(streamUrl), 0L)
    }

    fun savePlaybackPosition(streamUrl: String, posMs: Long) {
        prefs.edit { putLong(positionKey(streamUrl), posMs) }
    }

    fun clearPlaybackPosition(streamUrl: String) {
        prefs.edit { remove(positionKey(streamUrl)) }
    }

    private fun positionKey(url: String) = KEY_POSITION_PREFIX + url.hashCode().toString()

    // ── Theme ─────────────────────────────────────────────────────────────

    var nightMode: Int
        get() = prefs.getInt(KEY_NIGHT_MODE, androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
        set(value) = prefs.edit { putInt(KEY_NIGHT_MODE, value) }

    // ── Convenience ───────────────────────────────────────────────────────

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

    fun recordingStreamUrl(filename: String): String {
        val scheme = if (useHttps) "https" else "http"
        val encoded = android.net.Uri.encode(filename)
        return "$scheme://$host:$port/file?file=$encoded"
    }

    // ── Migration: single-device → multi-device ───────────────────────────

    private fun migrateIfNeeded() {
        if (prefs.contains(KEY_DEVICE_PROFILES)) return
        val oldHost = prefs.getString(KEY_HOST, "") ?: ""
        if (oldHost.isBlank()) return
        val profile = DeviceProfile(
            name = oldHost,
            host = oldHost,
            port = prefs.getInt(KEY_PORT, 80),
            useHttps = prefs.getBoolean(KEY_HTTPS, false),
            username = prefs.getString(KEY_USER, "") ?: "",
            password = prefs.getString(KEY_PASS, "") ?: ""
        )
        prefs.edit {
            putString(KEY_DEVICE_PROFILES, gson.toJson(listOf(profile)))
            putString(KEY_ACTIVE_DEVICE_ID, profile.id)
        }
    }

    companion object {
        private const val PREFS_NAME = "enigma2_prefs"
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_HTTPS = "use_https"
        const val KEY_USER = "username"
        const val KEY_PASS = "password"
        const val KEY_HIDDEN_BOUQUETS = "hidden_bouquets"
        const val KEY_FAVORITES = "favorite_service_refs"
        const val KEY_DEVICE_PROFILES = "device_profiles"
        const val KEY_ACTIVE_DEVICE_ID = "active_device_id"
        private const val KEY_POSITION_PREFIX = "pos_"
        private const val KEY_NIGHT_MODE = "night_mode"
    }
}
