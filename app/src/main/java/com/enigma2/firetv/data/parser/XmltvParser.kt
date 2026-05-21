package com.enigma2.firetv.data.parser

import android.util.Xml
import com.enigma2.firetv.data.model.IptvEpgEvent
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Streaming XMLTV parser. Uses Android's [XmlPullParser] so it never loads the
 * entire document into memory — safe for the large files provided by EPGSHARE01.
 *
 * Only events that fall within a ±4 h / +48 h window around "now" are kept.
 * [channelIds] can be used to restrict parsing to channels in the M3U playlist;
 * pass an empty set to accept all channels.
 */
object XmltvParser {

    private val dtFmtZone = ThreadLocal.withInitial {
        SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
    }
    private val dtFmtPlain = ThreadLocal.withInitial {
        SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
    }

    fun parse(stream: InputStream, channelIds: Set<String>): Map<String, List<IptvEpgEvent>> {
        val result = mutableMapOf<String, MutableList<IptvEpgEvent>>()
        val nowMs         = System.currentTimeMillis()
        val startCutoffMs = nowMs - 4L  * 60 * 60 * 1000   // 4 h ago
        val endCutoffMs   = nowMs + 48L * 60 * 60 * 1000   // 48 h ahead

        try {
            val parser = Xml.newPullParser()
            parser.setInput(stream, null)
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "programme") {
                    val channel = parser.getAttributeValue(null, "channel") ?: ""
                    if (channelIds.isEmpty() || channel in channelIds) {
                        val startMs = parseTime(parser.getAttributeValue(null, "start") ?: "")
                        val stopMs  = parseTime(parser.getAttributeValue(null, "stop")  ?: "")
                        var title = ""
                        var desc  = ""
                        // Read children until </programme>
                        loop@ while (true) {
                            val inner = parser.next()
                            when {
                                inner == XmlPullParser.END_DOCUMENT -> break@loop
                                inner == XmlPullParser.END_TAG && parser.name == "programme" -> break@loop
                                inner == XmlPullParser.START_TAG && parser.name == "title" && title.isEmpty() ->
                                    title = parser.nextText()
                                inner == XmlPullParser.START_TAG && parser.name == "desc" && desc.isEmpty() ->
                                    desc = parser.nextText()
                            }
                        }
                        if (title.isNotBlank() && startMs in startCutoffMs..endCutoffMs) {
                            result.getOrPut(channel) { mutableListOf() }
                                .add(IptvEpgEvent(channel, title, startMs, stopMs, desc))
                        }
                        eventType = parser.eventType
                        continue
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            // Best-effort: return what was parsed so far
        }

        return result.mapValues { (_, v) -> v.sortedBy { it.startMs } }
    }

    fun parseTime(str: String): Long {
        if (str.isBlank()) return 0L
        val s = str.trim()
        return try {
            dtFmtZone.get()!!.parse(s)?.time ?: 0L
        } catch (_: Exception) {
            try {
                dtFmtPlain.get()!!.parse(s.take(14))?.time ?: 0L
            } catch (_: Exception) {
                0L
            }
        }
    }
}
