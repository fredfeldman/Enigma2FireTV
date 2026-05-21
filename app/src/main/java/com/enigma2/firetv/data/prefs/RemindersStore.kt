package com.enigma2.firetv.data.prefs

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent store for EPG reminders. Backed by SharedPreferences with a JSON
 * payload (mirrors [PlaylistPreferences] pattern). Reminders are local-only and
 * fire via [com.enigma2.firetv.service.ReminderReceiver].
 *
 * Pref-key parity with Enigma2Android (`reminders_prefs`/`list`) is intentional
 * so cross-app profile imports remain forward-compatible per plan decision.
 */
data class EpgReminder(
    val id: Int,
    val title: String,
    val channelName: String,
    val sref: String,
    val startTimestampSec: Long
)

class RemindersStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun all(): List<EpgReminder> {
        val raw = prefs.getString(KEY_LIST, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                EpgReminder(
                    id = o.getInt("id"),
                    title = o.optString("title"),
                    channelName = o.optString("channel"),
                    sref = o.optString("sref"),
                    startTimestampSec = o.optLong("start")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun add(reminder: EpgReminder) {
        val list = all().toMutableList().apply {
            removeAll { it.id == reminder.id }
            add(reminder)
        }
        write(list)
    }

    fun remove(id: Int) {
        write(all().filter { it.id != id })
    }

    /** Generates a new id derived from sref+start; collision-safe enough for local use. */
    fun newId(sref: String, startSec: Long): Int = (sref + ":" + startSec).hashCode()

    private fun write(list: List<EpgReminder>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("title", r.title)
                put("channel", r.channelName)
                put("sref", r.sref)
                put("start", r.startTimestampSec)
            })
        }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "reminders_prefs"
        private const val KEY_LIST = "list"
    }
}
