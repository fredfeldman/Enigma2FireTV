package com.enigma2.firetv.data.parser

import com.enigma2.firetv.data.model.IptvChannel

/**
 * Parses M3U / M3U+ playlist content into [IptvChannel] objects.
 *
 * Handles the standard EXTINF attributes:
 *   tvg-id, tvg-name, tvg-logo, group-title
 */
object M3uParser {

    fun parse(content: String): List<IptvChannel> {
        val channels = mutableListOf<IptvChannel>()
        val lines = content.lines()
        var pendingExtinf: String? = null

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("#EXTINF:") -> pendingExtinf = trimmed
                trimmed.isNotBlank() && !trimmed.startsWith("#") && pendingExtinf != null -> {
                    parseChannel(pendingExtinf!!, trimmed)?.let { channels.add(it) }
                    pendingExtinf = null
                }
            }
        }
        return channels
    }

    private fun parseChannel(extinf: String, streamUrl: String): IptvChannel? {
        val tvgId  = extractAttr(extinf, "tvg-id") ?: ""
        val tvgName = extractAttr(extinf, "tvg-name") ?: ""
        val logo   = extractAttr(extinf, "tvg-logo") ?: ""
        val group  = extractAttr(extinf, "group-title")?.takeIf { it.isNotBlank() } ?: "General"
        // Display name is after the last comma on the EXTINF line
        val displayName = extinf.substringAfterLast(",").trim()
            .ifBlank { tvgName.ifBlank { tvgId } }
        if (displayName.isBlank()) return null
        return IptvChannel(
            tvgId     = tvgId.ifBlank { displayName },
            name      = displayName,
            logoUrl   = logo,
            group     = group,
            streamUrl = streamUrl
        )
    }

    private fun extractAttr(text: String, attr: String): String? =
        Regex("""$attr="([^"]*)"""").find(text)?.groupValues?.get(1)
}
