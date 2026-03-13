package com.enigma2.firetv.utils

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Sends a Wake-on-LAN magic packet to the broadcast address on UDP port 9.
 *
 * The magic packet is: 6 bytes of 0xFF followed by the 6-byte MAC address
 * repeated 16 times (102 bytes total).
 *
 * Must be called from a background thread.
 */
object WakeOnLan {

    private const val WOL_PORT = 9

    /**
     * Sends a WOL magic packet for the given [macAddress] (e.g. "AA:BB:CC:DD:EE:FF").
     * Returns true if the packet was sent without error, false otherwise.
     * Call this from a coroutine on [kotlinx.coroutines.Dispatchers.IO].
     */
    fun send(macAddress: String): Boolean {
        return try {
            val macBytes = parseMac(macAddress) ?: return false
            val packet = buildMagicPacket(macBytes)
            DatagramSocket().use { socket ->
                socket.broadcast = true
                val address = InetAddress.getByName("255.255.255.255")
                val datagram = DatagramPacket(packet, packet.size, address, WOL_PORT)
                socket.send(datagram)
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("WakeOnLan", "Failed to send WOL packet", e)
            false
        }
    }

    private fun parseMac(mac: String): ByteArray? {
        val clean = mac.replace(":", "").replace("-", "").replace(".", "")
        if (clean.length != 12) return null
        return try {
            ByteArray(6) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun buildMagicPacket(macBytes: ByteArray): ByteArray {
        val packet = ByteArray(6 + 16 * 6)
        // First 6 bytes: 0xFF
        for (i in 0 until 6) packet[i] = 0xFF.toByte()
        // Repeat MAC 16 times
        for (rep in 0 until 16) {
            System.arraycopy(macBytes, 0, packet, 6 + rep * 6, 6)
        }
        return packet
    }
}
