package com.enigma2.firetv.ui.epgassign

import android.app.AlertDialog
import android.content.Context
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.enigma2.firetv.R
import com.enigma2.firetv.data.repository.Enigma2Repository
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * EPG-source assignment dialog.
 *
 * Flow:
 * 1. Fetch source list from epgassign/sources.
 * 2. User picks a source → fetch its channel list from epgassign/source?name=…
 * 3. Channels are ranked by name similarity to [serviceName]; user picks one.
 * 4. POST epgassign/assign?sref=&channelId=&source=&name=.
 * 5. Optionally trigger epgassign/import (EPGImport run).
 *
 * v1.2.0 Phase 7.2 — UI present, gated by [BuildConfig.ENABLE_EPG_ASSIGN].
 */
object EpgAssignDialog {

    private val repo = Enigma2Repository()

    fun show(context: Context, owner: LifecycleOwner, serviceRef: String, serviceName: String) {
        val loading = ProgressBar(context)
        val dialog = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.epgassign_title, serviceName))
            .setView(loading)
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        owner.lifecycleScope.launch {
            // Step 1: fetch source list
            val srcJson = repo.epgAssignSources()
            if (srcJson == null) {
                dialog.setMessage(context.getString(R.string.epgassign_unavailable))
                return@launch
            }
            val sources = parseSources(srcJson)
            if (sources.isEmpty()) {
                dialog.setMessage(context.getString(R.string.epgassign_no_sources))
                return@launch
            }
            dialog.dismiss()
            pickSource(context, owner, serviceRef, serviceName, sources)
        }
    }

    private fun pickSource(
        context: Context, owner: LifecycleOwner,
        serviceRef: String, serviceName: String, sources: List<String>
    ) {
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.epgassign_pick_source))
            .setItems(sources.toTypedArray()) { _, idx ->
                owner.lifecycleScope.launch {
                    loadChannels(context, owner, serviceRef, serviceName, sources[idx])
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private suspend fun loadChannels(
        context: Context, owner: LifecycleOwner,
        serviceRef: String, serviceName: String, source: String
    ) {
        val loading = ProgressBar(context)
        val dialog = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.epgassign_loading_channels))
            .setView(loading)
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        val chJson = repo.epgAssignSource(source)
        dialog.dismiss()
        if (chJson == null) {
            Toast.makeText(context, context.getString(R.string.epgassign_channels_failed), Toast.LENGTH_LONG).show()
            return
        }
        val channels = parseChannels(chJson)
        if (channels.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.epgassign_no_channels), Toast.LENGTH_LONG).show()
            return
        }
        // Rank by name similarity (Jaro-Winkler approximation: just count common prefix chars)
        val ranked = channels.sortedByDescending { similarityScore(serviceName, it.first) }
        pickChannel(context, owner, serviceRef, serviceName, source, ranked)
    }

    private fun pickChannel(
        context: Context, owner: LifecycleOwner,
        serviceRef: String, serviceName: String,
        source: String, channels: List<Pair<String, String>>
    ) {
        val labels = channels.map { it.first }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.epgassign_pick_channel))
            .setItems(labels) { _, idx ->
                val (chName, chId) = channels[idx]
                owner.lifecycleScope.launch {
                    doAssign(context, owner, serviceRef, serviceName, source, chId, chName)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private suspend fun doAssign(
        context: Context, owner: LifecycleOwner,
        serviceRef: String, serviceName: String,
        source: String, channelId: String, channelName: String
    ) {
        val ok = repo.epgAssign(serviceRef, channelId, source, channelName)
        if (!ok) {
            Toast.makeText(context, context.getString(R.string.epgassign_assign_failed), Toast.LENGTH_LONG).show()
            return
        }
        // Offer to run EPGImport now
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.epgassign_assigned_title))
            .setMessage(context.getString(R.string.epgassign_assigned_msg, channelName))
            .setPositiveButton(context.getString(R.string.epgassign_run_import)) { _, _ ->
                owner.lifecycleScope.launch {
                    repo.epgAssignImport()
                    Toast.makeText(context, context.getString(R.string.epgassign_import_triggered), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ── Parsers ───────────────────────────────────────────────────────────────

    private fun parseSources(json: String): List<String> = runCatching {
        when (val root = JSONObject(json)) {
            else -> {
                val arr = root.optJSONArray("sources") ?: return@runCatching emptyList<String>()
                (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
            }
        }
    }.getOrDefault(emptyList())

    /** Returns list of (name, id) pairs from a source channel list. */
    private fun parseChannels(json: String): List<Pair<String, String>> = runCatching {
        val root = JSONObject(json)
        val arr = root.optJSONArray("channels") ?: return@runCatching emptyList<Pair<String, String>>()
        (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val id   = obj.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = obj.optString("name").ifBlank { id }
            name to id
        }
    }.getOrDefault(emptyList())

    /** Simple name similarity: count of matching characters from start. */
    private fun similarityScore(a: String, b: String): Int {
        val an = a.lowercase().filter { it.isLetterOrDigit() }
        val bn = b.lowercase().filter { it.isLetterOrDigit() }
        val minLen = minOf(an.length, bn.length)
        var score = 0
        for (i in 0 until minLen) { if (an[i] == bn[i]) score++ else break }
        // bonus for containing the other
        if (an.contains(bn) || bn.contains(an)) score += 3
        return score
    }
}
