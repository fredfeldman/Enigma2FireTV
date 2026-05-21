package com.enigma2.firetv.data.model

data class IptvChannel(
    val tvgId: String,
    val name: String,
    val logoUrl: String,
    val group: String,
    val streamUrl: String
)

data class IptvEpgEvent(
    val channelId: String,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val description: String = ""
)
