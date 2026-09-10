package takagi.ru.monica.steam.importer

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RustSteamMaFileParserTest {
    @Test
    fun decodesVersionedBridgePayload() {
        val payload = ByteArrayOutputStream().apply {
            write(byteArrayOf('M'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), '1'.code.toByte()))
            writeString("76561198000000000")
            writeString("alice")
            writeString("Alice")
            writeString("android:abc")
            writeString("AAECAwQFBgcICQoLDA0ODxAREhM=")
            writeOptionalString("identity")
            writeOptionalString(null)
            writeOptionalString("gid")
            writeOptionalString("access")
            writeOptionalString("refresh")
            writeOptionalString("76561198000000000||access")
            writeString("{\"steamid\":\"76561198000000000\"}")
        }.toByteArray()

        val decoded = RustSteamMaFileParser.decodeForTest(payload)
        requireNotNull(decoded)
        assertEquals("76561198000000000", decoded.steamId)
        assertEquals("alice", decoded.accountName)
        assertEquals("Alice", decoded.displayName)
        assertEquals("android:abc", decoded.deviceId)
        assertEquals("AAECAwQFBgcICQoLDA0ODxAREhM=", decoded.sharedSecret)
        assertEquals("identity", decoded.identitySecret)
        assertNull(decoded.revocationCode)
        assertEquals("gid", decoded.tokenGid)
        assertEquals("access", decoded.accessToken)
        assertEquals("refresh", decoded.refreshToken)
        assertEquals("76561198000000000||access", decoded.steamLoginSecure)
        assertTrue(decoded.hasRealSteamId)
    }

    @Test
    fun rejectsUnknownVersionAndTrailingBytes() {
        val unknownVersion = byteArrayOf(
            'M'.code.toByte(),
            'F'.code.toByte(),
            'I'.code.toByte(),
            '2'.code.toByte()
        )
        assertNull(RustSteamMaFileParser.decodeForTest(unknownVersion))

        val minimal = ByteArrayOutputStream().apply {
            write(byteArrayOf('M'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), '1'.code.toByte()))
            repeat(5) { writeString("") }
            repeat(6) { writeIntLe(0) }
            writeString("")
            write(7)
        }.toByteArray()
        assertNull(RustSteamMaFileParser.decodeForTest(minimal))
    }

    private fun ByteArrayOutputStream.writeOptionalString(value: String?) {
        if (value == null) {
            writeIntLe(0)
        } else {
            writeIntLe(1)
            writeString(value)
        }
    }

    private fun ByteArrayOutputStream.writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeIntLe(bytes.size)
        write(bytes)
    }

    private fun ByteArrayOutputStream.writeIntLe(value: Int) {
        write(value and 0xff)
        write((value ushr 8) and 0xff)
        write((value ushr 16) and 0xff)
        write((value ushr 24) and 0xff)
    }
}
