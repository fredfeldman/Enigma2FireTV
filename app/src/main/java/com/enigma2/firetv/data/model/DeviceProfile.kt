package com.enigma2.firetv.data.model

import java.util.UUID

data class DeviceProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 80,
    val useHttps: Boolean = false,
    val username: String = "",
    val password: String = "",
    /** Optional MAC address for Wake-on-LAN, e.g. "AA:BB:CC:DD:EE:FF" */
    val macAddress: String = ""
)
