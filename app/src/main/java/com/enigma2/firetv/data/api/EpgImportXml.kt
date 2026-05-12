package com.enigma2.firetv.data.api

import android.util.Xml
import com.enigma2.firetv.data.model.EpgImportCategory
import com.enigma2.firetv.data.model.EpgImportSource
import com.enigma2.firetv.data.model.EpgImportSourcesFile
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * Parses EPGImport `*.sources.xml` files served by OpenWebif's `/file` endpoint.
 *
 * The format (see EPGImport plugin's `EPGConfig.py`):
 * ```xml
 * <sources>
 *   <mappings> ... </mappings>           <!-- ignored, channel mappings -->
 *   <sourcecat sourcecatname="Rytec General XMLTV">
 *     <source type="gen_xmltv" channels="rytec.channels.xml.xz">
 *       <description>News Channels (xz)</description>
 *       <url>http://...</url>
 *       <url>http://...</url>           <!-- multiple mirrors per source -->
 *     </source>
 *   </sourcecat>
 * </sources>
 * ```
 *
 * Sources may also appear directly under `<sources>` with no enclosing
 * `<sourcecat>` — those are grouped under an empty-name category.
 */
object EpgImportXml {

    /**
     * Parses a `.sources.xml` document. The [path] and [displayName] are passed
     * through to the resulting model so callers can show the original filename.
     */
    fun parseSourcesFile(
        path: String,
        displayName: String,
        stream: InputStream
    ): EpgImportSourcesFile {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(stream, null)

        val categories = mutableMapOf<String, MutableList<EpgImportSource>>()
        // Stack: current category name (null = top-level "uncategorised").
        var currentCat: String? = null

        var sourceType = ""
        var sourceChannels: String? = null
        var sourceDescription = ""
        var sourceUrls = mutableListOf<String>()
        var inSource = false
        var inMappings = false

        val text = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    text.setLength(0)
                    when (name) {
                        // Skip `<mappings>` entirely — channel-id → service-ref maps.
                        "mappings" -> inMappings = true
                        "sourcecat" -> if (!inMappings) {
                            currentCat = parser.getAttributeValue(null, "sourcecatname").orEmpty()
                        }
                        "source" -> if (!inMappings) {
                            inSource = true
                            sourceType = parser.getAttributeValue(null, "type").orEmpty()
                            sourceChannels = parser.getAttributeValue(null, "channels")
                            sourceDescription = ""
                            sourceUrls = mutableListOf()
                        }
                    }
                }
                XmlPullParser.TEXT -> if (inSource) text.append(parser.text)
                XmlPullParser.END_TAG -> {
                    val name = parser.name
                    when (name) {
                        "mappings" -> inMappings = false
                        "sourcecat" -> if (!inMappings) currentCat = null
                        "description" -> if (inSource) sourceDescription = text.toString().trim()
                        "url" -> if (inSource) {
                            val u = text.toString().trim()
                            if (u.isNotEmpty()) sourceUrls.add(u)
                        }
                        "source" -> if (inSource) {
                            inSource = false
                            val cat = currentCat ?: ""
                            categories.getOrPut(cat) { mutableListOf() }.add(
                                EpgImportSource(
                                    description = sourceDescription,
                                    type = sourceType,
                                    channels = sourceChannels,
                                    urls = sourceUrls.toList()
                                )
                            )
                        }
                    }
                    text.setLength(0)
                }
            }
            event = parser.next()
        }

        return EpgImportSourcesFile(
            path = path,
            displayName = displayName,
            categories = categories.entries
                .map { (name, sources) -> EpgImportCategory(name, sources) }
        )
    }
}
