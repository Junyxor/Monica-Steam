package takagi.ru.monica.steam.core

import java.util.Base64

object SteamLoginApprovalSigner {
    fun signature(
        sharedSecretBase64: String,
        version: Int,
        clientId: Long,
        steamId: Long
    ): ByteArray = RustSteamCoreNative.generateLoginApprovalSignatureOrNull(
        sharedSecretBase64 = sharedSecretBase64,
        version = version,
        clientId = clientId,
        steamId = steamId
    ) ?: kotlinSignature(
        sharedSecretBase64 = sharedSecretBase64,
        version = version,
        clientId = clientId,
        steamId = steamId
    )

    fun tokenSignature(
        sharedSecretBase64: String,
        tokenId: Long
    ): ByteArray = RustSteamCoreNative.generateLoginTokenSignatureOrNull(
        sharedSecretBase64 = sharedSecretBase64,
        tokenId = tokenId
    ) ?: kotlinTokenSignature(
        sharedSecretBase64 = sharedSecretBase64,
        tokenId = tokenId
    )

    private fun kotlinSignature(
        sharedSecretBase64: String,
        version: Int,
        clientId: Long,
        steamId: Long
    ): ByteArray {
        val key = Base64.getDecoder().decode(sharedSecretBase64.trim())
        val payload = littleEndian16(version) + littleEndian64(clientId) + littleEndian64(steamId)
        return SteamTotp.hmac("HmacSHA256", key, payload)
    }

    private fun kotlinTokenSignature(
        sharedSecretBase64: String,
        tokenId: Long
    ): ByteArray {
        val key = Base64.getDecoder().decode(sharedSecretBase64.trim())
        return SteamTotp.hmac("HmacSHA256", key, littleEndian64(tokenId))
    }

    private fun littleEndian16(value: Int): ByteArray = byteArrayOf(
        (value and 0xff).toByte(),
        ((value shr 8) and 0xff).toByte()
    )

    private fun littleEndian64(value: Long): ByteArray {
        var current = value
        return ByteArray(8) {
            val byte = (current and 0xffL).toByte()
            current = current shr 8
            byte
        }
    }
}
