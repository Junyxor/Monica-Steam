package takagi.ru.monica.steam.token.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import takagi.ru.monica.steam.core.RustSteamCoreNative
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter

internal data class SteamAuthenticatorProtocolData(
    val sharedSecret: String?,
    val serialNumber: String?,
    val revocationCode: String?,
    val uri: String?,
    val serverTime: Long?,
    val accountName: String?,
    val tokenGid: String?,
    val identitySecret: String?,
    val secret1: String?,
    val status: Int,
    val phoneHint: String,
    val confirmType: Int,
    val steamGuardScheme: Int?,
    val steamId: String?
)

internal data class SteamFinalizeAuthenticatorProtocolData(
    val success: Boolean,
    val wantMore: Boolean,
    val status: Int
)

/** Rust-first wire boundary for ITwoFactorService authenticator transfer/enrollment calls. */
internal object SteamTwoFactorProtocol {
    fun buildAddAuthenticatorRequest(
        steamId: String,
        authTime: Long,
        deviceId: String
    ): ByteArray? {
        RustSteamCoreNative.buildAddAuthenticatorOrNull(steamId, authTime, deviceId)
            ?.let { return it }
        val steamIdLong = steamId.toLongOrNull() ?: return null
        return SteamProtoWriter().apply {
            writeFixed64(1, steamIdLong)
            writeUint64(2, authTime)
            writeVarint(4, 1L)
            writeString(5, deviceId)
            writeString(6, "1")
            writeVarint(8, 2L)
        }.toByteArray()
    }

    fun parseAddAuthenticatorResponse(response: ByteArray): SteamAuthenticatorProtocolData? {
        RustSteamCoreNative.parseAddAuthenticatorOrNull(response)
            ?.let(::decodeAuthenticator)
            ?.let { return it }
        val fields = runCatching { SteamProtoReader(response).parse() }.getOrNull() ?: return null
        return SteamAuthenticatorProtocolData(
            sharedSecret = fields[1]?.bytes
                ?.takeIf { it.isNotEmpty() }
                ?.let { Base64.getEncoder().encodeToString(it) },
            serialNumber = fields[2]?.asFixed64UnsignedString?.takeIf { it != "0" },
            revocationCode = fields[3]?.asString?.takeIf { it.isNotBlank() },
            uri = fields[4]?.asString?.takeIf { it.isNotBlank() },
            serverTime = fields[5]?.asLong,
            accountName = fields[6]?.asString?.takeIf { it.isNotBlank() },
            tokenGid = fields[7]?.asString?.takeIf { it.isNotBlank() },
            identitySecret = fields[8]?.bytes
                ?.takeIf { it.isNotEmpty() }
                ?.let { Base64.getEncoder().encodeToString(it) },
            secret1 = fields[9]?.bytes
                ?.takeIf { it.isNotEmpty() }
                ?.let { Base64.getEncoder().encodeToString(it) },
            status = fields[10]?.asInt ?: 0,
            phoneHint = fields[11]?.asString.orEmpty(),
            confirmType = fields[12]?.asInt ?: 0,
            steamGuardScheme = fields[11]?.asInt?.takeIf { it != 0 },
            steamId = fields[12]?.asFixed64UnsignedString?.takeIf { it != "0" }
        )
    }

    fun buildFinalizeAuthenticatorRequest(
        steamId: String,
        authenticatorCode: String,
        authTime: Long,
        activationCode: String,
        validateSmsCode: Boolean
    ): ByteArray? {
        RustSteamCoreNative.buildFinalizeAuthenticatorOrNull(
            steamId = steamId,
            authenticatorCode = authenticatorCode,
            authTime = authTime,
            activationCode = activationCode,
            validateSmsCode = validateSmsCode
        )?.let { return it }
        val steamIdLong = steamId.toLongOrNull() ?: return null
        return SteamProtoWriter().apply {
            writeFixed64(1, steamIdLong)
            writeString(2, authenticatorCode)
            writeUint64(3, authTime)
            writeString(4, activationCode)
            writeBool(6, validateSmsCode)
        }.toByteArray()
    }

    fun parseFinalizeAuthenticatorResponse(
        response: ByteArray
    ): SteamFinalizeAuthenticatorProtocolData? {
        RustSteamCoreNative.parseFinalizeAuthenticatorOrNull(response)
            ?.let(::decodeFinalize)
            ?.let { return it }
        val fields = runCatching { SteamProtoReader(response).parse() }.getOrNull() ?: return null
        return SteamFinalizeAuthenticatorProtocolData(
            success = fields[1]?.asBool ?: false,
            wantMore = fields[2]?.asBool ?: false,
            status = fields[4]?.asInt ?: 0
        )
    }

    fun buildReplaceContinueRequest(code: String): ByteArray {
        RustSteamCoreNative.buildReplaceAuthenticatorContinueOrNull(code)?.let { return it }
        return SteamProtoWriter().apply {
            writeString(1, code.trim())
            writeBool(2, true)
            writeVarint(3, 2L)
        }.toByteArray()
    }

    fun parseReplaceContinueResponse(response: ByteArray): SteamAuthenticatorProtocolData? {
        RustSteamCoreNative.parseReplaceAuthenticatorContinueOrNull(response)
            ?.let(::decodeAuthenticator)
            ?.let { return it }
        val fields = runCatching { SteamProtoReader(response).parse() }.getOrNull() ?: return null
        val replacementFields = fields[2]?.bytes?.let { bytes ->
            runCatching { SteamProtoReader(bytes).parse() }.getOrNull()
        } ?: return null
        return SteamAuthenticatorProtocolData(
            sharedSecret = replacementFields[1]?.bytes
                ?.takeIf { it.isNotEmpty() }
                ?.let { Base64.getEncoder().encodeToString(it) },
            serialNumber = replacementFields[2]?.asFixed64UnsignedString?.takeIf { it != "0" },
            revocationCode = replacementFields[3]?.asString?.takeIf { it.isNotBlank() },
            uri = replacementFields[4]?.asString?.takeIf { it.isNotBlank() },
            serverTime = replacementFields[5]?.asLong,
            accountName = replacementFields[6]?.asString?.takeIf { it.isNotBlank() },
            tokenGid = replacementFields[7]?.asString?.takeIf { it.isNotBlank() },
            identitySecret = replacementFields[8]?.bytes
                ?.takeIf { it.isNotEmpty() }
                ?.let { Base64.getEncoder().encodeToString(it) },
            secret1 = replacementFields[9]?.bytes
                ?.takeIf { it.isNotEmpty() }
                ?.let { Base64.getEncoder().encodeToString(it) },
            status = replacementFields[10]?.asInt ?: 0,
            phoneHint = replacementFields[11]?.asString.orEmpty(),
            confirmType = replacementFields[12]?.asInt ?: 0,
            steamGuardScheme = replacementFields[11]?.asInt?.takeIf { it != 0 },
            steamId = replacementFields[12]?.asFixed64UnsignedString?.takeIf { it != "0" }
        )
    }

    private fun decodeAuthenticator(payload: ByteArray): SteamAuthenticatorProtocolData? =
        runCatching {
            val buffer = bridgeBuffer(payload, AUTHENTICATOR_MAGIC)
            val result = SteamAuthenticatorProtocolData(
                sharedSecret = readOptionalString(buffer),
                serialNumber = readOptionalString(buffer),
                revocationCode = readOptionalString(buffer),
                uri = readOptionalString(buffer),
                serverTime = readOptionalLong(buffer),
                accountName = readOptionalString(buffer),
                tokenGid = readOptionalString(buffer),
                identitySecret = readOptionalString(buffer),
                secret1 = readOptionalString(buffer),
                status = readInt(buffer),
                phoneHint = readString(buffer),
                confirmType = readInt(buffer),
                steamGuardScheme = readOptionalInt(buffer),
                steamId = readOptionalString(buffer)
            )
            require(!buffer.hasRemaining()) { "Rust two-factor bridge has trailing bytes" }
            result
        }.getOrNull()

    private fun decodeFinalize(payload: ByteArray): SteamFinalizeAuthenticatorProtocolData? =
        runCatching {
            val buffer = bridgeBuffer(payload, FINALIZE_MAGIC)
            val result = SteamFinalizeAuthenticatorProtocolData(
                success = readInt(buffer) != 0,
                wantMore = readInt(buffer) != 0,
                status = readInt(buffer)
            )
            require(!buffer.hasRemaining()) { "Rust finalize bridge has trailing bytes" }
            result
        }.getOrNull()

    private fun bridgeBuffer(payload: ByteArray, expectedMagic: ByteArray): ByteBuffer {
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        require(buffer.remaining() >= expectedMagic.size) { "Rust two-factor bridge is truncated" }
        val magic = ByteArray(expectedMagic.size).also(buffer::get)
        require(magic.contentEquals(expectedMagic)) { "Rust two-factor bridge version is unsupported" }
        return buffer
    }

    private fun readOptionalString(buffer: ByteBuffer): String? = when (readInt(buffer)) {
        0 -> null
        1 -> readString(buffer)
        else -> error("Rust two-factor optional-string flag is invalid")
    }

    private fun readOptionalLong(buffer: ByteBuffer): Long? = when (readInt(buffer)) {
        0 -> null
        1 -> {
            require(buffer.remaining() >= Long.SIZE_BYTES) { "Rust two-factor long is truncated" }
            buffer.long
        }
        else -> error("Rust two-factor optional-long flag is invalid")
    }

    private fun readOptionalInt(buffer: ByteBuffer): Int? = when (readInt(buffer)) {
        0 -> null
        1 -> readInt(buffer)
        else -> error("Rust two-factor optional-int flag is invalid")
    }

    private fun readInt(buffer: ByteBuffer): Int {
        require(buffer.remaining() >= Int.SIZE_BYTES) { "Rust two-factor int is truncated" }
        return buffer.int
    }

    private fun readString(buffer: ByteBuffer): String {
        val length = readInt(buffer)
        require(length >= 0 && length <= MAX_STRING_BYTES && length <= buffer.remaining()) {
            "Rust two-factor string length is invalid"
        }
        return ByteArray(length).also(buffer::get).toString(Charsets.UTF_8)
    }

    private val AUTHENTICATOR_MAGIC = "TFA1".toByteArray(Charsets.US_ASCII)
    private val FINALIZE_MAGIC = "TFF1".toByteArray(Charsets.US_ASCII)
    private const val MAX_STRING_BYTES = 8 * 1024 * 1024
}
