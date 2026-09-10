package takagi.ru.monica.steam.importer

import java.nio.ByteBuffer
import java.nio.ByteOrder
import takagi.ru.monica.steam.core.RustSteamCoreNative

internal object RustSteamMaFileParser {
    fun parseOrNull(
        plainJson: String,
        fileName: String?,
        displayNameOverride: String?,
        steamIdOverride: String?,
        allowMissingSteamId: Boolean
    ): SteamMaFilePayload? {
        return RustSteamCoreNative.parseMaFileJsonOrNull(
            plainJson = plainJson,
            fileName = fileName,
            displayNameOverride = displayNameOverride,
            steamIdOverride = steamIdOverride,
            allowMissingSteamId = allowMissingSteamId
        )?.let(::decode)
    }

    internal fun decodeForTest(payload: ByteArray): SteamMaFilePayload? = decode(payload)

    private fun decode(payload: ByteArray): SteamMaFilePayload? = runCatching {
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        require(buffer.remaining() >= MAGIC.size) { "Rust maFile bridge is truncated" }
        require(readMagic(buffer).contentEquals(MAGIC)) {
            "Rust maFile bridge version is unsupported"
        }

        val decoded = SteamMaFilePayload(
            steamId = readString(buffer),
            accountName = readString(buffer),
            displayName = readString(buffer),
            deviceId = readString(buffer),
            sharedSecret = readString(buffer),
            identitySecret = readOptionalString(buffer),
            revocationCode = readOptionalString(buffer),
            tokenGid = readOptionalString(buffer),
            accessToken = readOptionalString(buffer),
            refreshToken = readOptionalString(buffer),
            steamLoginSecure = readOptionalString(buffer),
            rawJson = readString(buffer)
        )
        require(!buffer.hasRemaining()) { "Rust maFile bridge has trailing bytes" }
        decoded
    }.getOrNull()

    private fun readOptionalString(buffer: ByteBuffer): String? {
        require(buffer.remaining() >= Int.SIZE_BYTES) {
            "Rust maFile optional-string flag is truncated"
        }
        return when (val present = buffer.int) {
            0 -> null
            1 -> readString(buffer)
            else -> error("Rust maFile optional-string flag is invalid: $present")
        }
    }

    private fun readString(buffer: ByteBuffer): String {
        require(buffer.remaining() >= Int.SIZE_BYTES) { "Rust maFile string is truncated" }
        val length = buffer.int
        require(length >= 0 && length <= MAX_STRING_BYTES && length <= buffer.remaining()) {
            "Rust maFile string length is invalid"
        }
        return ByteArray(length).also(buffer::get).toString(Charsets.UTF_8)
    }

    private fun readMagic(buffer: ByteBuffer): ByteArray = ByteArray(MAGIC.size).also(buffer::get)

    private val MAGIC = byteArrayOf(
        'M'.code.toByte(),
        'F'.code.toByte(),
        'I'.code.toByte(),
        '1'.code.toByte()
    )
    private const val MAX_STRING_BYTES = 16 * 1024 * 1024
}
