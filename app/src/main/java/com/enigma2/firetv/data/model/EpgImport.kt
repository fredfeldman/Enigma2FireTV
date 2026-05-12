package com.enigma2.firetv.data.model

/**
 * One `*.sources.xml` file from `/etc/epgimport/`.
 *
 * Categories (`<sourcecat>`) carry the bundle the user sees in the EPGImport
 * plugin UI. Sources without an enclosing category are grouped under an
 * empty-name category.
 */
data class EpgImportSourcesFile(
    val path: String,
    val displayName: String,
    val categories: List<EpgImportCategory>
) {
    /** Total source count across all categories. Useful for list summaries. */
    val sourceCount: Int get() = categories.sumOf { it.sources.size }
}

data class EpgImportCategory(
    val name: String,
    val sources: List<EpgImportSource>
)

data class EpgImportSource(
    val description: String,
    /** `gen_xmltv`, `epg.dat`, etc. */
    val type: String,
    /** Optional companion `*.channels.xml` filename (mappings file). */
    val channels: String?,
    /** Mirror URLs for this source (one or more). */
    val urls: List<String>
)
