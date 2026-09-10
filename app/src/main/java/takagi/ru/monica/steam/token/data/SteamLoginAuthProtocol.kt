package takagi.ru.monica.steam.token.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import takagi.ru.monica.steam.core.RustSteamCoreNative
import takagi.ru.monica.steam.network.SteamProtoField
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.token.loginsecurity.data.SteamMobileAuthRequestProfile

internal data class SteamLoginAuthChallengeData(
    val confirmationType: Int,
    val associatedMessage: String
)

internal data class SteamLoginBeginCredentialsData(
    val clientId: String,
    val requestId: String,
    val steamId: String,
    val challenges: List<SteamLoginAuthChallengeData>,
    val message: String?
)

internal data class SteamLoginBeginQrData(
    val clientId: String,
    val requestId: String,
    val challengeUrl: String,
    val challenges: List<SteamLoginAuthChallengeData>
)

internal data class SteamLoginPollData(
    val clientId: String?,
    val challengeUrl: String?,
    val refreshToken: String?,
    val accessToken: String?,
    val accountName: String?
)

internal data class SteamLoginAccessTokenData(
    val accessToken: String?,
    val refreshToken: String?
)

/**
 * Coarse protocol boundary for IAuthenticationService login traffic.
 *
 * Each native call owns a complete protobuf request or response. The Kotlin
 * implementation stays here as a compatibility fallback instead of leaking
 * Steam wire-format details back into the login orchestrator.
 */
internal object SteamLoginAuthProtocol {
    fun buildBeginCredentialsRequest(
        userName: String,
        encryptedPassword: String,
        encryptionTimestamp: String
    ): ByteArray {
        RustSteamCoreNative.buildLoginBeginCredentialsOrNull(
            deviceFriendlyName = SteamMobileAuthRequestProfile.deviceFriendlyName,
            userName = userName,
            encryptedPassword = encryptedPassword,
            encryptionTimestamp = encryptionTimestamp,
            platformType = SteamMobileAuthRequestProfile.platformType,
            osType = SteamMobileAuthRequestProfile.osType,
            gamingDeviceType = SteamMobileAuthRequestProfile.gamingDeviceType,
            websiteId = SteamMobileAuthRequestProfile.websiteId
        )?.let { return it }

        val timestamp = encryptionTimestamp.toULongOrNull()
            ?: throw IllegalStateException("Steam returned an invalid RSA timestamp")
        return SteamProtoWriter().apply {
            writeString(1, SteamMobileAuthRequestProfile.deviceFriendlyName)
            writeString(2, userName)
            writeString(3, encryptedPassword)
            writeUint64(4, timestamp.toLong())
            writeBool(5, false)
            writeVarint(6, SteamMobileAuthRequestProfile.platformType)
            writeVarint(7, 1L)
            writeString(8, SteamMobileAuthRequestProfile.websiteId)
            writeMessage(9, buildDeviceDetails())
            writeString(10, "")
            writeVarint(11, 0L)
            writeVarint(12, 2L)
        }.toByteArray()
    }

    fun parseBeginCredentialsResponse(response: ByteArray): SteamLoginBeginCredentialsData? {
        RustSteamCoreNative.parseLoginBeginCredentialsOrNull(response)
            ?.let(::decodeBeginCredentials)
            ?.let { return it }

        val fields = runCatching { SteamProtoReader(response).parseAll() }.getOrNull() ?: return null
        val clientId = fields.firstOrNull { it.number == 1 }
            ?.asLong
            ?.takeIf { it != 0L }
            ?.toULong()
            ?.toString()
        val requestId = fields.firstOrNull { it.number == 2 }
            ?.bytes
            ?.takeIf { it.isNotEmpty() }
            ?.let { Base64.getEncoder().encodeToString(it) }
        val steamId = fields.firstOrNull { it.number == 5 }
            ?.asLong
            ?.takeIf { it != 0L }
            ?.toULong()
            ?.toString()
        if (clientId.isNullOrBlank() || requestId.isNullOrBlank() || steamId.isNullOrBlank()) {
            return null
        }
        return SteamLoginBeginCredentialsData(
            clientId = clientId,
            requestId = requestId,
            steamId = steamId,
            challenges = fields.allowedConfirmations(4),
            message = fields.firstOrNull { it.number == 8 }
                ?.asString
                ?.takeIf { it.isNotBlank() }
        )
    }

    fun buildBeginQrRequest(): ByteArray {
        RustSteamCoreNative.buildLoginBeginQrOrNull(
            deviceFriendlyName = SteamMobileAuthRequestProfile.deviceFriendlyName,
            platformType = SteamMobileAuthRequestProfile.platformType,
            osType = SteamMobileAuthRequestProfile.osType,
            gamingDeviceType = SteamMobileAuthRequestProfile.gamingDeviceType,
            websiteId = SteamMobileAuthRequestProfile.websiteId
        )?.let { return it }

        return SteamProtoWriter().apply {
            writeString(1, SteamMobileAuthRequestProfile.deviceFriendlyName)
            writeVarint(2, 3L)
            writeMessage(3, buildDeviceDetails())
            writeString(4, SteamMobileAuthRequestProfile.websiteId)
        }.toByteArray()
    }

    fun parseBeginQrResponse(response: ByteArray): SteamLoginBeginQrData? {
        RustSteamCoreNative.parseLoginBeginQrOrNull(response)
            ?.let(::decodeBeginQr)
            ?.let { return it }

        val fields = runCatching { SteamProtoReader(response).parseAll() }.getOrNull() ?: return null
        val clientId = fields.firstOrNull { it.number == 1 }
            ?.asLong
            ?.takeIf { it != 0L }
            ?.toULong()
            ?.toString()
        val challengeUrl = fields.firstOrNull { it.number == 2 }
            ?.asString
            ?.takeIf { it.isNotBlank() }
        val requestId = fields.firstOrNull { it.number == 3 }
            ?.bytes
            ?.takeIf { it.isNotEmpty() }
            ?.let { Base64.getEncoder().encodeToString(it) }
        if (clientId.isNullOrBlank() || challengeUrl.isNullOrBlank() || requestId.isNullOrBlank()) {
            return null
        }
        return SteamLoginBeginQrData(
            clientId = clientId,
            requestId = requestId,
            challengeUrl = challengeUrl,
            challenges = fields.allowedConfirmations(5)
        )
    }

    fun buildUpdateGuardRequest(
        clientId: String,
        steamId: String,
        code: String,
        confirmationType: Int
    ): ByteArray? {
        RustSteamCoreNative.buildLoginUpdateGuardOrNull(
            clientId = clientId,
            steamId = steamId,
            code = code,
            confirmationType = confirmationType
        )?.let { return it }

        val clientIdBits = clientId.trim().toULongOrNull() ?: return null
        val steamIdBits = steamId.trim().toULongOrNull() ?: return null
        return SteamProtoWriter().apply {
            writeUint64(1, clientIdBits.toLong())
            writeFixed64(2, steamIdBits.toLong())
            writeString(3, code.trim())
            writeVarint(4, confirmationType.toLong())
        }.toByteArray()
    }

    fun buildPollRequest(
        clientId: String,
        requestId: String,
        tokenToRevoke: String? = null
    ): ByteArray? {
        RustSteamCoreNative.buildLoginPollOrNull(
            clientId = clientId,
            requestId = requestId,
            tokenToRevoke = tokenToRevoke
        )?.let { return it }

        val clientIdBits = clientId.trim().toULongOrNull() ?: return null
        val requestIdBytes = decodeRequestId(requestId) ?: return null
        val tokenBits = tokenToRevoke
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.toULongOrNull()
            ?: if (tokenToRevoke.isNullOrBlank()) null else return null
        return SteamProtoWriter().apply {
            writeUint64(1, clientIdBits.toLong())
            writeBytes(2, requestIdBytes)
            tokenBits?.let { writeFixed64(3, it.toLong()) }
        }.toByteArray()
    }

    fun parsePollResponse(response: ByteArray): SteamLoginPollData? {
        RustSteamCoreNative.parseLoginPollOrNull(response)
            ?.let(::decodePoll)
            ?.let { return it }

        val fields = runCatching { SteamProtoReader(response).parse() }.getOrNull() ?: return null
        return SteamLoginPollData(
            clientId = fields[1]?.asLong
                ?.takeIf { it != 0L }
                ?.toULong()
                ?.toString(),
            challengeUrl = fields[2]?.asString?.takeIf { it.isNotBlank() },
            refreshToken = fields[3]?.asString?.takeIf { it.isNotBlank() },
            accessToken = fields[4]?.asString?.takeIf { it.isNotBlank() },
            accountName = fields[6]?.asString?.takeIf { it.isNotBlank() }
        )
    }

    fun buildGenerateAccessTokenRequest(refreshToken: String, steamId: String): ByteArray? {
        RustSteamCoreNative.buildLoginAccessTokenOrNull(refreshToken, steamId)?.let { return it }
        val steamIdBits = steamId.trim().toULongOrNull() ?: return null
        return SteamProtoWriter().apply {
            writeString(1, refreshToken)
            writeFixed64(2, steamIdBits.toLong())
        }.toByteArray()
    }

    fun parseGenerateAccessTokenResponse(response: ByteArray): SteamLoginAccessTokenData? {
        RustSteamCoreNative.parseLoginAccessTokenOrNull(response)
            ?.let(::decodeAccessToken)
            ?.let { return it }

        val fields = runCatching { SteamProtoReader(response).parse() }.getOrNull() ?: return null
        return SteamLoginAccessTokenData(
            accessToken = fields[1]?.asString?.takeIf { it.isNotBlank() },
            refreshToken = fields[2]?.asString?.takeIf { it.isNotBlank() }
        )
    }

    private fun buildDeviceDetails(): SteamProtoWriter = SteamProtoWriter().apply {
        writeString(1, SteamMobileAuthRequestProfile.deviceFriendlyName)
        writeVarint(2, SteamMobileAuthRequestProfile.platformType)
        writeVarint(3, SteamMobileAuthRequestProfile.osType)
        writeVarint(4, SteamMobileAuthRequestProfile.gamingDeviceType)
    }

    private fun List<SteamProtoField>.allowedConfirmations(
        confirmationField: Int
    ): List<SteamLoginAuthChallengeData> {
        return filter { it.number == confirmationField && it.bytes != null }.mapNotNull { field ->
            val confirmation = runCatching {
                SteamProtoReader(field.bytes ?: return@mapNotNull null).parse()
            }.getOrNull() ?: return@mapNotNull null
            val type = confirmation[1]?.asInt ?: return@mapNotNull null
            if (type == 0) return@mapNotNull null
            SteamLoginAuthChallengeData(
                confirmationType = type,
                associatedMessage = confirmation[2]?.asString.orEmpty()
            )
        }
    }

    private fun decodeRequestId(value: String): ByteArray? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        val compact = trimmed.filterNot(Char::isWhitespace)
        val padded = compact + "=".repeat((4 - compact.length % 4) % 4)
        val decoded = sequenceOf(
            runCatching { Base64.getDecoder().decode(padded) }.getOrNull(),
            runCatching { Base64.getUrlDecoder().decode(padded) }.getOrNull(),
            runCatching { Base64.getMimeDecoder().decode(trimmed) }.getOrNull()
        ).firstOrNull { !it.isNullOrEmpty() }
        if (decoded != null) return decoded
        return decodeHex(trimmed) ?: trimmed.toByteArray(Charsets.UTF_8)
    }

    private fun decodeHex(value: String): ByteArray? {
        if (value.isEmpty() || value.length % 2 != 0 || value.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) {
            return null
        }
        return value.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
            .takeIf { it.isNotEmpty() }
    }

    private fun decodeBeginCredentials(payload: ByteArray): SteamLoginBeginCredentialsData? =
        runCatching {
            val buffer = bridgeBuffer(payload, BEGIN_CREDENTIALS_MAGIC)
            val result = SteamLoginBeginCredentialsData(
                clientId = readString(buffer),
                requestId = readString(buffer),
                steamId = readString(buffer),
                message = readOptionalString(buffer),
                challenges = readChallenges(buffer)
            )
            require(!buffer.hasRemaining()) { "Rust login credentials bridge has trailing bytes" }
            result
        }.getOrNull()

    private fun decodeBeginQr(payload: ByteArray): SteamLoginBeginQrData? = runCatching {
        val buffer = bridgeBuffer(payload, BEGIN_QR_MAGIC)
        val result = SteamLoginBeginQrData(
            clientId = readString(buffer),
            requestId = readString(buffer),
            challengeUrl = readString(buffer),
            challenges = readChallenges(buffer)
        )
        require(!buffer.hasRemaining()) { "Rust login QR bridge has trailing bytes" }
        result
    }.getOrNull()

    private fun decodePoll(payload: ByteArray): SteamLoginPollData? = runCatching {
        val buffer = bridgeBuffer(payload, POLL_MAGIC)
        val result = SteamLoginPollData(
            clientId = readOptionalString(buffer),
            challengeUrl = readOptionalString(buffer),
            refreshToken = readOptionalString(buffer),
            accessToken = readOptionalString(buffer),
            accountName = readOptionalString(buffer)
        )
        require(!buffer.hasRemaining()) { "Rust login poll bridge has trailing bytes" }
        result
    }.getOrNull()

    private fun decodeAccessToken(payload: ByteArray): SteamLoginAccessTokenData? = runCatching {
        val buffer = bridgeBuffer(payload, ACCESS_TOKEN_MAGIC)
        val result = SteamLoginAccessTokenData(
            accessToken = readOptionalString(buffer),
            refreshToken = readOptionalString(buffer)
        )
        require(!buffer.hasRemaining()) { "Rust login access-token bridge has trailing bytes" }
        result
    }.getOrNull()

    private fun bridgeBuffer(payload: ByteArray, expectedMagic: ByteArray): ByteBuffer {
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        require(buffer.remaining() >= expectedMagic.size) { "Rust login bridge is truncated" }
        val magic = ByteArray(expectedMagic.size).also(buffer::get)
        require(magic.contentEquals(expectedMagic)) { "Rust login bridge version is unsupported" }
        return buffer
    }

    private fun readChallenges(buffer: ByteBuffer): List<SteamLoginAuthChallengeData> {
        require(buffer.remaining() >= Int.SIZE_BYTES) { "Rust login challenge count is truncated" }
        val count = buffer.int
        require(count in 0..MAX_CHALLENGES) { "Rust login challenge count is invalid" }
        return List(count) {
            require(buffer.remaining() >= Int.SIZE_BYTES) { "Rust login challenge is truncated" }
            SteamLoginAuthChallengeData(
                confirmationType = buffer.int,
                associatedMessage = readString(buffer)
            )
        }
    }

    private fun readOptionalString(buffer: ByteBuffer): String? {
        require(buffer.remaining() >= Int.SIZE_BYTES) { "Rust login optional-string flag is truncated" }
        return when (val flag = buffer.int) {
            0 -> null
            1 -> readString(buffer)
            else -> error("Rust login optional-string flag is invalid: $flag")
        }
    }

    private fun readString(buffer: ByteBuffer): String {
        require(buffer.remaining() >= Int.SIZE_BYTES) { "Rust login string is truncated" }
        val length = buffer.int
        require(length >= 0 && length <= MAX_STRING_BYTES && length <= buffer.remaining()) {
            "Rust login string length is invalid"
        }
        return ByteArray(length).also(buffer::get).toString(Charsets.UTF_8)
    }

    private val BEGIN_CREDENTIALS_MAGIC = "LAC1".toByteArray(Charsets.US_ASCII)
    private val BEGIN_QR_MAGIC = "LAQ1".toByteArray(Charsets.US_ASCII)
    private val POLL_MAGIC = "LAP1".toByteArray(Charsets.US_ASCII)
    private val ACCESS_TOKEN_MAGIC = "LAT1".toByteArray(Charsets.US_ASCII)
    private const val MAX_CHALLENGES = 128
    private const val MAX_STRING_BYTES = 8 * 1024 * 1024
}
