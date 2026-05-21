package com.enigma2.firetv.data.model

import java.util.UUID

data class IptvSource(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val m3uUrl: String,
    val epgUrl: String = ""
)
