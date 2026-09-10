package takagi.ru.monica.steam.token.data

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter

class SteamLoginAuthProtocolTest {
    @Test
    fun credentialsFallbackKeepsSteamMobileWireShape() {
        val request = SteamLoginAuthProtocol.buildBeginCredentialsRequest(
            userName = "alice",
            encryptedPassword = "encrypted",
            encryptionTimestamp = "1700000000"
        )
        val fields = SteamProtoReader(request).parseAll()

        assertEquals("Monica Steam", fields.first { it.number == 1 }.asString)
        assertEquals("alice", fields.first { it.number == 2 }.asString)
        assertEquals("encrypted", fields.first { it.number == 3 }.asString)
        assertEquals(1_700_000_000L, fields.first { it.number == 4 }.asLong)
        assertEquals(0L, fields.first { it.number == 5 }.asLong)
        assertEquals(3L, fields.first { it.number == 6 }.asLong)
        assertEquals(1L, fields.first { it.number == 7 }.asLong)
        assertEquals("Mobile", fields.first { it.number == 8 }.asString)

        val device = SteamProtoReader(requireNotNull(fields.first { it.number == 9 }.bytes)).parse()
        assertEquals("Monica Steam", device[1]?.asString)
        assertEquals(3L, device[2]?.asLong)
        assertEquals(-500L, device[3]?.asLong)
        assertEquals(528L, device[4]?.asLong)
    }

    @Test
    fun credentialsParserPreservesUnsignedClientAndChallengeOrder() {
        val firstChallenge = SteamProtoWriter().apply {
            writeVarint(1, 3L)
            writeString(2, "device")
        }
        val secondChallenge = SteamProtoWriter().apply {
            writeVarint(1, 2L)
            writeString(2, "email")
        }
        val response = SteamProtoWriter().apply {
            writeUint64(1, "18446744073709551615")
            writeBytes(2, "request".toByteArray())
            writeMessage(4, firstChallenge)
            writeMessage(4, secondChallenge)
            writeUint64(5, 76_561_198_000_000_000L)
            writeString(8, "hello")
        }.toByteArray()

        val parsed = requireNotNull(SteamLoginAuthProtocol.parseBeginCredentialsResponse(response))
        assertEquals("18446744073709551615", parsed.clientId)
        assertEquals("76561198000000000", parsed.steamId)
        assertEquals(Base64.getEncoder().encodeToString("request".toByteArray()), parsed.requestId)
        assertEquals(listOf(3, 2), parsed.challenges.map { it.confirmationType })
        assertEquals(listOf("device", "email"), parsed.challenges.map { it.associatedMessage })
        assertEquals("hello", parsed.message)
    }

    @Test
    fun pollCodecKeepsUnsignedBitsAndLastDuplicateResponseSemantics() {
        val request = requireNotNull(
            SteamLoginAuthProtocol.buildPollRequest(
                clientId = "18446744073709551615",
                requestId = Base64.getEncoder().encodeToString("request".toByteArray()),
                tokenToRevoke = "18446744073709551614"
            )
        )
        val requestFields = SteamProtoReader(request).parse()
        assertEquals(-1L, requestFields[1]?.asLong)
        assertTrue(requestFields[2]?.bytes?.contentEquals("request".toByteArray()) == true)
        assertEquals("18446744073709551614", requestFields[3]?.asFixed64UnsignedString)

        val response = SteamProtoWriter().apply {
            writeUint64(1, 7L)
            writeUint64(1, "18446744073709551615")
            writeString(3, "old-refresh")
            writeString(3, "refresh")
            writeString(4, "access")
            writeString(6, "alice")
        }.toByteArray()
        val parsed = requireNotNull(SteamLoginAuthProtocol.parsePollResponse(response))
        assertEquals("18446744073709551615", parsed.clientId)
        assertEquals("refresh", parsed.refreshToken)
        assertEquals("access", parsed.accessToken)
        assertEquals("alice", parsed.accountName)
        assertNull(parsed.challengeUrl)
    }

    @Test
    fun generatedAccessTokenCodecParsesRotatedRefreshToken() {
        val request = requireNotNull(
            SteamLoginAuthProtocol.buildGenerateAccessTokenRequest(
                refreshToken = "refresh",
                steamId = "76561198000000000"
            )
        )
        val requestFields = SteamProtoReader(request).parse()
        assertEquals("refresh", requestFields[1]?.asString)
        assertEquals("76561198000000000", requestFields[2]?.asFixed64UnsignedString)

        val response = SteamProtoWriter().apply {
            writeString(1, "access")
            writeString(2, "refresh-2")
        }.toByteArray()
        val parsed = requireNotNull(SteamLoginAuthProtocol.parseGenerateAccessTokenResponse(response))
        assertEquals("access", parsed.accessToken)
        assertEquals("refresh-2", parsed.refreshToken)
    }
}
