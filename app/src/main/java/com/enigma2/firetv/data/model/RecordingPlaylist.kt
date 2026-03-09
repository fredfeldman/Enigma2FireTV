package com.enigma2.firetv.data.model

import java.util.UUID

/** A slim snapshot of a recording stored inside a playlist (no live API data needed). */
data class PlaylistEntry(
    val filename: String,
    val title: String,
    val channel: String = "",
    val durationSec: Int = 0
)

data class RecordingPlaylist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val entries: List<PlaylistEntry> = emptyList()
)
