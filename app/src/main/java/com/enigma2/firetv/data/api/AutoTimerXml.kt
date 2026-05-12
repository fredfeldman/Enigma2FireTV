package com.enigma2.firetv.data.api

import android.util.Xml
import com.enigma2.firetv.data.model.AutoTimer
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * Tolerant parser for the AutoTimer plugin's XML output.
 *
 * Expected shape (only the attributes we care about are read; everything else is ignored):
 * ```
 * <autotimer version="…">
 *   <timer id="1" name="…" match="…" enabled="yes"
 *          from="20:00" to="22:30"
 *          searchType="partial" searchCase="insensitive">
 *     <e2service>
 *       <e2servicereference>1:0:1:…</e2servicereference>
 *       <e2servicename>BBC One</e2servicename>
 *     </e2service>
 *   </timer>
 * </autotimer>
 * ```
 *
 * Also recognises `<e2state>True</e2state>` / `<e2statetext>…</e2statetext>`
 * "simple result" responses returned by /autotimer/edit and /autotimer/remove.
 */
object AutoTimerXml {

    data class SimpleResult(val ok: Boolean, val message: String?)

    fun parseList(stream: InputStream): List<AutoTimer> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(stream, null)

        val result = mutableListOf<AutoTimer>()
        var current: Builder? = null
        var pendingRef: String? = null
        var text = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    text.setLength(0)
                    when (parser.name) {
                        "timer" -> current = Builder().apply {
                            id = parser.getAttributeValue(null, "id")?.toIntOrNull() ?: -1
                            name = parser.getAttributeValue(null, "name").orEmpty()
                            match = parser.getAttributeValue(null, "match").orEmpty()
                            enabled = parser.getAttributeValue(null, "enabled")
                                ?.equals("yes", ignoreCase = true) ?: true
                            after = parser.getAttributeValue(null, "from")
                            before = parser.getAttributeValue(null, "to")
                            searchType = parser.getAttributeValue(null, "searchType") ?: "partial"
                            searchCase = parser.getAttributeValue(null, "searchCase") ?: "insensitive"
                        }
                        "e2service" -> pendingRef = null
                    }
                }
                XmlPullParser.TEXT -> text.append(parser.text)
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "timer" -> current?.let { result.add(it.build()); current = null }
                        "e2servicereference" -> pendingRef = text.toString().trim()
                        "e2servicename" -> {
                            val ref = pendingRef
                            if (ref != null && current != null) {
                                current!!.services.add(ref)
                                current!!.serviceNames.add(text.toString().trim())
                                pendingRef = null
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }
        return result
    }

    /**
     * Parses the simple `<e2simplexmlresult><e2state>True</e2state>…</e2simplexmlresult>`
     * style response. Falls back to ok=true if the body is not in the expected shape
     * (some OpenWebif versions return a plain HTTP 200 with empty body).
     */
    fun parseSimpleResult(stream: InputStream): SimpleResult {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(stream, null)

        var ok = true
        var message: String? = null
        val text = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> text.setLength(0)
                XmlPullParser.TEXT -> text.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "e2state" -> ok = text.toString().trim().equals("true", ignoreCase = true)
                    "e2statetext" -> message = text.toString().trim()
                }
            }
            event = parser.next()
        }
        return SimpleResult(ok, message)
    }

    private class Builder {
        var id: Int = -1
        var name: String = ""
        var match: String = ""
        var enabled: Boolean = true
        var after: String? = null
        var before: String? = null
        var searchType: String = "partial"
        var searchCase: String = "insensitive"
        val services = mutableListOf<String>()
        val serviceNames = mutableListOf<String>()
        fun build() = AutoTimer(
            id = id, name = name, match = match, enabled = enabled,
            services = services.toList(), serviceNames = serviceNames.toList(),
            after = after, before = before,
            searchType = searchType, searchCase = searchCase
        )
    }
}
