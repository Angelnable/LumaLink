package com.lumalink.g100d.protocol

import java.io.DataInputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Read-only PTP/IP handshake probe.
 *
 * It sends only InitCommandRequest and reads InitCommandAck; it does not open a
 * PTP session, enumerate objects, read files, or write any camera setting.
 * This is used to decide whether the LUMIX Sync path is PTP/IP.
 */
object PtpIpProbe {
    suspend fun probe(
        host: String,
        port: Int = 15740,
        includeProtocolVersion: Boolean = true,
        timeoutMs: Int = 2_000,
    ): LumixResult<String> =
        withContext(Dispatchers.IO) {
            if (!HostGuard.isLocalHost(host)) {
                return@withContext LumixResult.Failure(
                    LumixError.ReadOnlyViolation("non-local PTP host: $host"),
                )
            }
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                socket.soTimeout = timeoutMs
                val output = socket.getOutputStream()
                val input = DataInputStream(socket.getInputStream())

                val name = "LumaLink"
                val nameBytes = ptpString(name)
                val guid = ByteArray(16) { index -> (0x40 + index).toByte() }
                val body = if (includeProtocolVersion) {
                    guid + nameBytes + le32(0x00010000)
                } else {
                    guid + nameBytes
                }
                writePacket(output, type = 1, body = body)

                val packet = readPacket(input)
                    ?: return@withContext LumixResult.Failure(
                        LumixError.ProtocolMismatch("PTP/IP: empty init response"),
                    )
                if (packet.type == 5) {
                    val errorCode = if (packet.body.size >= 4) readLe32(packet.body, 0) else -1
                    return@withContext LumixResult.Failure(
                        LumixError.ProtocolMismatch("PTP/IP InitFail code=$errorCode"),
                    )
                }
                if (packet.type != 2) {
                    return@withContext LumixResult.Failure(
                        LumixError.ProtocolMismatch("PTP/IP: unexpected init type ${packet.type}"),
                    )
                }
                val connectionNumber = readLe32(packet.body, 0)
                val cameraName = parsePtpString(packet.body, 20)
                LumixResult.Success(
                    "PTP/IP init ok connection=$connectionNumber camera=$cameraName",
                )
            } catch (error: Throwable) {
                LumixResult.Failure(
                    LumixError.Offline(error.message ?: "PTP/IP probe failed", error),
                )
            } finally {
                runCatching { socket.close() }
            }
        }

    private fun writePacket(output: OutputStream, type: Int, body: ByteArray) {
        val length = 8 + body.size
        output.write(le32(length))
        output.write(le32(type))
        output.write(body)
        output.flush()
    }

    private data class Packet(val type: Int, val body: ByteArray)

    private fun readPacket(input: DataInputStream): Packet? {
        val length = readLe32(input) ?: return null
        if (length < 8 || length > 1_000_000) return null
        val type = readLe32(input) ?: return null
        val body = ByteArray(length - 8)
        input.readFully(body)
        return Packet(type, body)
    }

    private fun readLe32(input: DataInputStream): Int? {
        val bytes = ByteArray(4)
        return try {
            input.readFully(bytes)
            readLe32(bytes, 0)
        } catch (_: Throwable) {
            null
        }
    }

    private fun le32(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 24) and 0xFF).toByte(),
    )

    private fun readLe32(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun ptpString(value: String): ByteArray {
        val chars = value.toCharArray()
        val bytes = ByteArray(1 + (chars.size + 1) * 2)
        bytes[0] = (chars.size + 1).toByte()
        chars.forEachIndexed { index, char ->
            bytes[1 + index * 2] = (char.code and 0xFF).toByte()
            bytes[1 + index * 2 + 1] = ((char.code ushr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private fun parsePtpString(bytes: ByteArray, offset: Int): String {
        if (offset >= bytes.size) return ""
        val length = bytes[offset].toInt() and 0xFF
        if (length <= 0 || offset + 1 + (length - 1) * 2 > bytes.size) return ""
        val chars = CharArray(length - 1)
        for (index in chars.indices) {
            val low = bytes[offset + 1 + index * 2].toInt() and 0xFF
            val high = bytes[offset + 2 + index * 2].toInt() and 0xFF
            chars[index] = ((high shl 8) or low).toChar()
        }
        return String(chars)
    }
}
