package takagi.ru.monica.steam.token.data

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter

class SteamTwoFactorProtocolTest {
    @Test
    fun addAuthenticatorFallbackKeepsWireShape() {
        val request = requireNotNull(
            SteamTwoFactorProtocol.buildAddAuthenticatorRequest(
                steamId = "76561198000000000",
                authTime = 1_700_000_000L,
                deviceId = "android:abc"
            )
        )
        val fields = SteamProtoReader(request).parse()
        assertEquals("76561198000000000", fields[1]?.asFixed64UnsignedString)
        assertEquals(1_700_000_000L, fields[2]?.asLong)
        assertEquals(1L, fields[4]?.asLong)
        assertEquals("android:abc", fields[5]?.asString)
        assertEquals("1", fields[6]?.asString)
        assertEquals(2L, fields[8]?.asLong)
    }

    @Test
    fun addAuthenticatorParserReturnsCanonicalSecretFields() {
        val shared = ByteArray(20) { it.toByte() }
        val identity = ByteArray(20) { (it + 20).toByte() }
        val response = SteamProtoWriter().apply {
            writeBytes(1, shared)
            writeFixed64(2, -1L)
            writeString(3, "R123")
            writeString(4, "otpauth://example")
            writeUint64(5, 1_700_000_000L)
            writeString(6, "alice")
            writeString(7, "gid")
            writeBytes(8, identity)
            writeBytes(9, "secret-one".toByteArray())
            writeVarint(10, 1L)
            writeString(11, "+1 **12")
            writeVarint(12, 3L)
        }.toByteArray()

        val parsed = requireNotNull(SteamTwoFactorProtocol.parseAddAuthenticatorResponse(response))
        assertEquals(Base64.getEncoder().encodeToString(shared), parsed.sharedSecret)
        assertEquals("18446744073709551615", parsed.serialNumber)
        assertEquals("R123", parsed.revocationCode)
        assertEquals("alice", parsed.accountName)
        assertEquals(Base64.getEncoder().encodeToString(identity), parsed.identitySecret)
        assertEquals(1, parsed.status)
        assertEquals("+1 **12", parsed.phoneHint)
        assertEquals(3, parsed.confirmType)
    }

    @Test
    fun finalizeCodecPreservesSuccessWantMoreAndStatus() {
        val request = requireNotNull(
            SteamTwoFactorProtocol.buildFinalizeAuthenticatorRequest(
                steamId = "76561198000000000",
                authenticatorCode = "ABCDE",
                authTime = 1_700_000_000L,
                activationCode = "12345",
                validateSmsCode = true
            )
        )
        val requestFields = SteamProtoReader(request).parse()
        assertEquals("ABCDE", requestFields[2]?.asString)
        assertEquals("12345", requestFields[4]?.asString)
        assertTrue(requestFields[6]?.asBool == true)

        val response = SteamProtoWriter().apply {
            writeBool(1, true)
            writeBool(2, false)
            writeVarint(4, 1L)
        }.toByteArray()
        val parsed = requireNotNull(
            SteamTwoFactorProtocol.parseFinalizeAuthenticatorResponse(response)
        )
        assertTrue(parsed.success)
        assertFalse(parsed.wantMore)
        assertEquals(1, parsed.status)
    }

    @Test
    fun replacementParserReadsNestedAuthenticatorPayload() {
        val replacement = SteamProtoWriter().apply {
            writeBytes(1, ByteArray(20) { 7 })
            writeFixed64(2, 42L)
            writeString(3, "R1")
            writeUint64(5, 1_700_000_000L)
            writeString(6, "alice")
            writeVarint(10, 1L)
            writeVarint(11, 2L)
            writeFixed64(12, 76_561_198_000_000_000L)
        }
        val response = SteamProtoWriter().apply {
            writeMessage(2, replacement)
        }.toByteArray()

        val parsed = requireNotNull(
            SteamTwoFactorProtocol.parseReplaceContinueResponse(response)
        )
        assertEquals("42", parsed.serialNumber)
        assertEquals("R1", parsed.revocationCode)
        assertEquals(1_700_000_000L, parsed.serverTime)
        assertEquals(1, parsed.status)
        assertEquals(2, parsed.steamGuardScheme)
        assertEquals("76561198000000000", parsed.steamId)
    }
}
