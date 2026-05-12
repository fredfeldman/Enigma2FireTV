package com.enigma2.firetv.data.api

import android.util.Xml
import com.enigma2.firetv.data.model.Bouquet
import com.enigma2.firetv.data.model.Service
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * Tolerant parser for the BouquetEditor plugin's XML output.
 *
 * The plugin returns `<e2simplexmlresult>` for mutations and an `<e2bouquets>`
 * tree (same shape as core OpenWebif `getservices`) for the user-bouquet list.
 *
 * Reuses the shared [com.enigma2.firetv.data.api.AutoTimerXml.SimpleResult]
 * ack shape so callers can branch on a single ok/message pair.
 */
object BouquetEditorXml {

    /**
     * Parses an `<e2simplexmlresult><e2state>True</e2state><e2statetext>…</e2statetext></e2simplexmlresult>`
     * body. Empty or non-XML bodies are treated as success (some images return HTTP 200
     * with no payload after a successful edit).
     */
    fun parseSimpleResult(stream: InputStream): AutoTimerXml.SimpleResult =
        AutoTimerXml.parseSimpleResult(stream)

    /**
     * Parses the user-bouquet list returned by `/bouqueteditor/api/getuserbouquets`.
     * Element shape mirrors core OpenWebif:
     * ```
     * <e2bouquets>
     *   <e2bouquet>
     *     <e2servicereference>1:7:1:0:0:0:…</e2servicereference>
     *     <e2servicename>Favourites (TV)</e2servicename>
     *     <e2services> … </e2services>
     *   </e2bouquet>
     * </e2bouquets>
     * ```
     * Inner `<e2services>` is ignored — callers re-fetch channels through the
     * normal `/api/getservices` endpoint when needed.
     */
    fun parseUserBouquets(stream: InputStream): List<Bouquet> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(stream, null)

        val out = mutableListOf<Bouquet>()
        var ref: String? = null
        var name: String? = null
        var inBouquet = false
        var depth = 0
        val text = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    text.setLength(0)
                    when (parser.name) {
                        "e2bouquet" -> { inBouquet = true; depth = 0; ref = null; name = null }
                        "e2services" -> if (inBouquet) depth++
                    }
                }
                XmlPullParser.TEXT -> text.append(parser.text)
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "e2servicereference" -> if (inBouquet && depth == 0)
                            ref = text.toString().trim()
                        "e2servicename" -> if (inBouquet && depth == 0)
                            name = text.toString().trim()
                        "e2services" -> if (inBouquet && depth > 0) depth--
                        "e2bouquet" -> {
                            if (!ref.isNullOrBlank()) {
                                out.add(Bouquet(ref = ref!!, name = name.orEmpty(), channels = null))
                            }
                            inBouquet = false
                        }
                    }
                }
            }
            event = parser.next()
        }
        return out
    }

    @Suppress("unused")
    private fun emptyServiceList(): List<Service> = emptyList()
}
