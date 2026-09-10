package takagi.ru.monica.steam.core

/**
 * Optional JNI bridge for the Rust Steam core.
 *
 * Local Android builds remain functional when the native library has not been
 * produced yet; callers transparently fall back to the Kotlin implementation.
 * Release CI packages the native library for the supported ABIs.
 */
internal object RustSteamCoreNative {
    private val loaded: Boolean = runCatching {
        System.loadLibrary("monica_steam_android")
        true
    }.getOrDefault(false)

    val isAvailable: Boolean
        get() = loaded

    fun generateAuthCodeOrNull(sharedSecretBase64: String, unixTimeSeconds: Long): String? {
        if (!loaded) return null
        return runCatching {
            nativeGenerateAuthCode(sharedSecretBase64, unixTimeSeconds)
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun generateConfirmationHashOrNull(
        identitySecretBase64: String,
        unixTimeSeconds: Long,
        tag: String
    ): String? {
        if (!loaded) return null
        return runCatching {
            nativeGenerateConfirmationHash(identitySecretBase64, unixTimeSeconds, tag)
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun generateLoginApprovalSignatureOrNull(
        sharedSecretBase64: String,
        version: Int,
        clientId: Long,
        steamId: Long
    ): ByteArray? {
        if (!loaded) return null
        return runCatching {
            nativeGenerateLoginApprovalSignature(
                sharedSecretBase64 = sharedSecretBase64,
                version = version,
                clientId = clientId,
                steamId = steamId
            )
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun generateLoginTokenSignatureOrNull(
        sharedSecretBase64: String,
        tokenId: Long
    ): ByteArray? {
        if (!loaded) return null
        return runCatching {
            nativeGenerateLoginTokenSignature(sharedSecretBase64, tokenId)
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun encodeCmMessageOrNull(
        eMsg: Int,
        steamId: Long,
        sessionId: Int,
        body: ByteArray,
        jobIdSource: Long,
        jobIdTarget: Long,
        targetJobName: String?
    ): ByteArray? {
        if (!loaded) return null
        return runCatching {
            nativeEncodeCmMessage(
                eMsg = eMsg,
                steamId = steamId,
                sessionId = sessionId,
                body = body,
                jobIdSource = jobIdSource,
                jobIdTarget = jobIdTarget,
                targetJobName = targetJobName.orEmpty()
            )
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun decodeCmMessagesOrNull(payload: ByteArray): ByteArray? {
        if (!loaded || payload.isEmpty()) return null
        return runCatching { nativeDecodeCmMessages(payload) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    fun parseChatSessionsOrNull(response: ByteArray): ByteArray? {
        if (!loaded || response.isEmpty()) return null
        return runCatching { nativeParseChatSessions(response) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    fun parseChatMessagesOrNull(response: ByteArray): ByteArray? {
        if (!loaded || response.isEmpty()) return null
        return runCatching { nativeParseChatMessages(response) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    fun parseOwnedGamesOrNull(response: ByteArray): ByteArray? {
        if (!loaded || response.isEmpty()) return null
        return runCatching { nativeParseOwnedGames(response) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    fun parseAchievementProgressOrNull(response: ByteArray): ByteArray? {
        if (!loaded || response.isEmpty()) return null
        return runCatching { nativeParseAchievementProgress(response) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    fun webLogonBodyOrNull(webLogonToken: String): ByteArray? {
        if (!loaded) return null
        return runCatching { nativeWebLogonBody(webLogonToken) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    @JvmStatic
    private external fun nativeGenerateAuthCode(
        sharedSecretBase64: String,
        unixTimeSeconds: Long
    ): String

    @JvmStatic
    private external fun nativeGenerateConfirmationHash(
        identitySecretBase64: String,
        unixTimeSeconds: Long,
        tag: String
    ): String

    @JvmStatic
    private external fun nativeGenerateLoginApprovalSignature(
        sharedSecretBase64: String,
        version: Int,
        clientId: Long,
        steamId: Long
    ): ByteArray

    @JvmStatic
    private external fun nativeGenerateLoginTokenSignature(
        sharedSecretBase64: String,
        tokenId: Long
    ): ByteArray

    @JvmStatic
    private external fun nativeEncodeCmMessage(
        eMsg: Int,
        steamId: Long,
        sessionId: Int,
        body: ByteArray,
        jobIdSource: Long,
        jobIdTarget: Long,
        targetJobName: String
    ): ByteArray

    @JvmStatic
    private external fun nativeDecodeCmMessages(payload: ByteArray): ByteArray

    @JvmStatic
    private external fun nativeParseChatSessions(response: ByteArray): ByteArray

    @JvmStatic
    private external fun nativeParseChatMessages(response: ByteArray): ByteArray

    @JvmStatic
    private external fun nativeParseOwnedGames(response: ByteArray): ByteArray

    @JvmStatic
    private external fun nativeParseAchievementProgress(response: ByteArray): ByteArray

    @JvmStatic
    private external fun nativeWebLogonBody(webLogonToken: String): ByteArray
}
