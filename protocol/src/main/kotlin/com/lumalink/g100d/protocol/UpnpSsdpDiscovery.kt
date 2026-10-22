package com.lumalink.g100d.protocol

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Minimal SSDP M-SEARCH discovery. It only asks for a MediaServer description
 * location; it does not send any camera control command.
 */
class UpnpSsdpDiscovery(
    private val timeoutMs: Int = 2_500,
) {
    suspend fun discoverLocation(): LumixResult<String> = withContext(Dispatchers.IO) {
        val targets = listOf(
            "urn:schemas-upnp-org:device:MediaServer:1",
            "ssdp:all",
        )
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 500
                val group = InetAddress.getByName("239.255.255.250")
                for (target in targets) {
                    val message = buildString {
                        append("M-SEARCH * HTTP/1.1\r\n")
                        append("HOST: 239.255.255.250:1900\r\n")
                        append("MAN: \"ssdp:discover\"\r\n")
                        append("MX: 2\r\n")
                        append("ST: $target\r\n")
                        append("\r\n")
                    }.toByteArray(Charsets.US_ASCII)
                    socket.send(DatagramPacket(message, message.size, group, 1900))
                }

                val deadline = System.currentTimeMillis() + timeoutMs
                val buffer = ByteArray(8 * 1024)
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val text = packet.data.copyOfRange(
                            packet.offset,
                            packet.offset + packet.length,
                        ).toString(Charsets.UTF_8)
                        parseLocation(text)?.let { location ->
                            if (HostGuard.isLocalHost(location.toHttpUrlHost())) {
                                return@withContext LumixResult.Success(location)
                            }
                        }
                    } catch (_: SocketTimeoutException) {
                        // Keep waiting until the overall deadline.
                    }
                }
            }
        } catch (error: Throwable) {
            return@withContext LumixResult.Failure(
                LumixError.Offline(error.message ?: "SSDP discovery failed", error),
            )
        }
        LumixResult.Failure(LumixError.ProtocolMismatch("SSDP returned no local LOCATION"))
    }

    private fun parseLocation(response: String): String? {
        return response.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    private fun String.toHttpUrlHost(): String =
        runCatching { toHttpUrl().host }.getOrDefault("")
}
