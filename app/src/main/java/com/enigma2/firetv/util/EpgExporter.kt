package com.enigma2.firetv.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.enigma2.firetv.data.model.EpgEvent
import com.google.gson.GsonBuilder
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Phase 4 EPG export helper. Writes XMLTV and JSON snapshots of an
 * in-memory EPG payload to the public Downloads folder.
 *
 * Uses the scoped-storage MediaStore API on Android 10+ and the legacy
 * Downloads directory on earlier releases. No runtime storage permission
 * is required on Android 10+; on older releases the caller is expected to
 * already hold `WRITE_EXTERNAL_STORAGE`.
 */
object EpgExporter {

    private const val SUBDIR = "Enigma2FireTV"
    private val XMLTV_FMT = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /** Result of an export attempt: file display name and whether write succeeded. */
    data class ExportResult(val displayName: String, val success: Boolean)

    /** Sanitises [channelName] into a filename-safe stem. */
    fun safeStem(channelName: String): String =
        channelName.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
            .ifBlank { "channel" }

    /** Writes [events] as an XMLTV document for the given channel. */
    fun exportXmltv(
        context: Context,
        channelName: String,
        channelRef: String,
        events: List<EpgEvent>
    ): ExportResult {
        val stem = safeStem(channelName)
        val name = "epg_${stem}_${todayStamp()}.xml"
        val body = buildXmltv(channelName, channelRef, events)
        val ok = writeToDownloads(context, name, "application/xml", body.toByteArray(Charsets.UTF_8))
        return ExportResult(name, ok)
    }

    /** Writes [events] as a pretty-printed JSON document for the given channel. */
    fun exportJson(
        context: Context,
        channelName: String,
        events: List<EpgEvent>
    ): ExportResult {
        val stem = safeStem(channelName)
        val name = "epg_${stem}_${todayStamp()}.json"
        val body = GsonBuilder().setPrettyPrinting().create().toJson(events)
        val ok = writeToDownloads(context, name, "application/json", body.toByteArray(Charsets.UTF_8))
        return ExportResult(name, ok)
    }

    private fun buildXmltv(channelName: String, channelRef: String, events: List<EpgEvent>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<tv generator-info-name=\"Enigma2FireTV\">\n")
        val channelId = safeStem(channelName)
        sb.append("  <channel id=\"").append(esc(channelId)).append("\">\n")
        sb.append("    <display-name>").append(esc(channelName)).append("</display-name>\n")
        sb.append("    <!-- sref: ").append(esc(channelRef)).append(" -->\n")
        sb.append("  </channel>\n")
        for (e in events) {
            val start = XMLTV_FMT.format(Date(e.beginMs))
            val stop = XMLTV_FMT.format(Date(e.endMs))
            sb.append("  <programme start=\"").append(start)
                .append("\" stop=\"").append(stop)
                .append("\" channel=\"").append(esc(channelId)).append("\">\n")
            sb.append("    <title>").append(esc(e.title)).append("</title>\n")
            e.shortDesc?.takeIf { it.isNotBlank() }?.let {
                sb.append("    <sub-title>").append(esc(it)).append("</sub-title>\n")
            }
            e.longDesc?.takeIf { it.isNotBlank() }?.let {
                sb.append("    <desc>").append(esc(it)).append("</desc>\n")
            }
            sb.append("  </programme>\n")
        }
        sb.append("</tv>\n")
        return sb.toString()
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun todayStamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())

    private fun writeToDownloads(
        context: Context,
        name: String,
        mime: String,
        bytes: ByteArray
    ): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/" + SUBDIR
                )
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri == null) false else {
                resolver.openOutputStream(uri)?.use { os: OutputStream ->
                    os.write(bytes)
                }
                true
            }
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                SUBDIR
            )
            if (!dir.exists()) dir.mkdirs()
            File(dir, name).outputStream().use { it.write(bytes) }
            true
        }
    } catch (e: Exception) {
        android.util.Log.e("EpgExporter", "write failed for $name", e)
        false
    }
}
