package com.enigma2.firetv.data.model

/**
 * A single AutoTimer rule on the receiver.
 *
 * AutoTimer is an Enigma2 plugin that watches the EPG for events whose title
 * matches a [match] string and automatically schedules a recording for any
 * hits. This model intentionally exposes only the most commonly used fields.
 *
 * @param id          Server-side numeric id (-1 for a new, not-yet-saved rule).
 * @param name        Friendly label shown in the AutoTimer list.
 * @param match       Substring that the event title must contain.
 * @param enabled     False = the rule is kept but ignored when the receiver scans EPG.
 * @param services    Service references the rule is restricted to. Empty = any channel.
 * @param serviceNames Parallel list of human-readable channel names (best-effort).
 * @param after       Optional "no earlier than HH:MM" filter.
 * @param before      Optional "no later than HH:MM" filter.
 * @param searchType  AutoTimer search type (partial / exact / start / description).
 * @param searchCase  "sensitive" or "insensitive".
 */
data class AutoTimer(
    val id: Int = -1,
    val name: String,
    val match: String,
    val enabled: Boolean = true,
    val services: List<String> = emptyList(),
    val serviceNames: List<String> = emptyList(),
    val after: String? = null,
    val before: String? = null,
    val searchType: String = "partial",
    val searchCase: String = "insensitive"
) {
    /** Short description used in the list row's secondary line. */
    fun describe(): String = buildString {
        append("\u201C").append(match).append("\u201D")
        if (services.isNotEmpty()) {
            append(" \u00b7 ")
            append(serviceNames.firstOrNull() ?: services.first())
            if (services.size > 1) append(" +").append(services.size - 1)
        } else {
            append(" \u00b7 any channel")
        }
        if (!after.isNullOrBlank() || !before.isNullOrBlank()) {
            append(" \u00b7 ")
            append(after ?: "—")
            append(" – ")
            append(before ?: "—")
        }
    }
}
