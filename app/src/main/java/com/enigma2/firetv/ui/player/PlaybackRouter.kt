package com.enigma2.firetv.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.enigma2.firetv.R
import com.enigma2.firetv.data.prefs.ReceiverPreferences
import com.enigma2.firetv.data.repository.Enigma2Repository

/**
 * Routes playback to either the internal ExoPlayer ([PlayerActivity]) or an
 * external video player app, depending on [ReceiverPreferences.playerMode].
 *
 * v1.2.0 Phase 5.3 — all existing call-sites continue to launch PlayerActivity
 * directly; new code may use [play] to honour the user preference.
 */
object PlaybackRouter {

    const val MODE_INTERNAL = "internal"
    const val MODE_EXTERNAL = "external"
    const val MODE_ASK      = "ask"

    /** Ordered list of recognised external player packages (names in [pkgLabel]). */
    val KNOWN_EXTERNALS = listOf(
        "org.videolan.vlc",
        "com.mxtech.videoplayer.ad",
        "com.mxtech.videoplayer.pro",
        "org.xbmc.kodi",
        "org.xbmc.kodi19",
        "org.xbmc.kodi20"
    )

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Plays [streamUrl] according to the player_mode pref.
     *
     * [channelRefs] / [channelNames] / [channelIndex] are forwarded to
     * PlayerActivity for channel-up/down navigation (internal player only).
     * [mime] defaults to MPEG-TS for live channels; callers pass
     * "video/mp4" or the detected type for recordings.
     */
    fun play(
        context: Context,
        streamUrl: String,
        channelName: String,
        serviceRef: String,
        mime: String = "video/mp2t",
        channelRefs: List<String> = emptyList(),
        channelNames: List<String> = emptyList(),
        channelIndex: Int = 0
    ) {
        // For IPTV refs, extract the real URL so external players receive a
        // plain HTTP URL rather than the Enigma2 service-ref stream path.
        val actualUrl = Enigma2Repository.extractIptvUrl(serviceRef) ?: streamUrl
        // IPTV MPEG-TS vs HLS detection (basic): treat .m3u8 as HLS.
        val actualMime = if (actualUrl.contains(".m3u8", ignoreCase = true) ||
                             actualUrl.contains("application/", ignoreCase = true))
            "application/x-mpegURL" else mime

        val prefs = ReceiverPreferences(context)
        when (prefs.playerMode) {
            MODE_EXTERNAL -> playExternal(context, actualUrl, actualMime, prefs.preferredExternalPackage)
            MODE_ASK -> {
                val installed = installedExternals(context)
                if (installed.isEmpty()) {
                    playInternal(context, actualUrl, channelName, serviceRef,
                                 channelRefs, channelNames, channelIndex)
                } else {
                    showChooser(context, actualUrl, actualMime, channelName, serviceRef,
                                installed, channelRefs, channelNames, channelIndex)
                }
            }
            else -> playInternal(context, actualUrl, channelName, serviceRef,
                                 channelRefs, channelNames, channelIndex)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun playInternal(
        context: Context,
        streamUrl: String,
        channelName: String,
        serviceRef: String,
        channelRefs: List<String>,
        channelNames: List<String>,
        channelIndex: Int
    ) {
        val intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_STREAM_URL, streamUrl)
            putExtra(PlayerActivity.EXTRA_CHANNEL_NAME, channelName)
            putExtra(PlayerActivity.EXTRA_SERVICE_REF, serviceRef)
            if (channelRefs.isNotEmpty()) {
                putStringArrayListExtra(PlayerActivity.EXTRA_CHANNEL_REFS, ArrayList(channelRefs))
                putStringArrayListExtra(PlayerActivity.EXTRA_CHANNEL_NAMES_LIST, ArrayList(channelNames))
                putExtra(PlayerActivity.EXTRA_CHANNEL_INDEX, channelIndex)
            }
        }
        context.startActivity(intent)
    }

    fun playExternal(context: Context, url: String, mime: String, preferredPkg: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), mime)
        if (preferredPkg.isNotBlank()) intent.setPackage(preferredPkg)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // Pass Basic-auth credentials to VLC via documented extras when set.
        // External players don't share our OkHttp interceptor.
        val prefs = ReceiverPreferences(context)
        if (prefs.username.isNotBlank()) {
            val creds = android.util.Base64.encodeToString(
                "${prefs.username}:${prefs.password}".toByteArray(),
                android.util.Base64.NO_WRAP
            )
            val headers = android.os.Bundle().apply {
                putString("Authorization", "Basic $creds")
            }
            // VLC-Android picks up these two extras for custom HTTP headers
            intent.putExtra("http-headers", headers)
            intent.putExtra("headers", headers)
        }

        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Launch failed: ${e.javaClass.simpleName}: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun showChooser(
        context: Context,
        url: String,
        mime: String,
        channelName: String,
        serviceRef: String,
        installed: List<String>,
        channelRefs: List<String>,
        channelNames: List<String>,
        channelIndex: Int
    ) {
        val labels = mutableListOf(context.getString(R.string.player_internal))
        installed.mapTo(labels) { pkgLabel(it) }
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.player_choose_title))
            .setItems(labels.toTypedArray()) { _, idx ->
                if (idx == 0) playInternal(context, url, channelName, serviceRef,
                                            channelRefs, channelNames, channelIndex)
                else          playExternal(context, url, mime, installed[idx - 1])
            }
            .show()
    }

    fun installedExternals(context: Context): List<String> {
        val pm = context.packageManager
        return KNOWN_EXTERNALS.filter { pkg ->
            try { pm.getPackageInfo(pkg, 0); true } catch (_: Exception) { false }
        }
    }

    fun pkgLabel(pkg: String): String = when (pkg) {
        "org.videolan.vlc"                                     -> "VLC"
        "com.mxtech.videoplayer.ad",
        "com.mxtech.videoplayer.pro"                           -> "MX Player"
        "org.xbmc.kodi", "org.xbmc.kodi19", "org.xbmc.kodi20" -> "Kodi"
        else                                                    -> pkg
    }
}
