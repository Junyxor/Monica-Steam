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

    fun parseStoreItemsOrNull(response: ByteArray): ByteArray? {
        if (!loaded || response.isEmpty()) return null
        return runCatching { nativeParseStoreItems(response) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    fun parseAchievementDetailsOrNull(
        definitionsResponse: ByteArray,
        userResponse: ByteArray
    ): ByteArray? {
        if (!loaded) return null
        return runCatching {
            nativeParseAchievementDetails(definitionsResponse, userResponse)
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun parseFamilySharedAppsOrNull(response: ByteArray): ByteArray? {
        if (!loaded || response.isEmpty()) return null
        return runCatching { nativeParseFamilySharedApps(response) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    fun parseFriendNicknamesOrNull(response: ByteArray): ByteArray? {
        if (!loaded || response.isEmpty()) return null
        return runCatching { nativeParseFriendNicknames(response) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    fun parsePendingLoginClientIdsOrNull(response: ByteArray): ByteArray? {
        if (!loaded || response.isEmpty()) return null
        return runCatching { nativeParsePendingLoginClientIds(response) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    fun parseAuthSessionInfoOrNull(response: ByteArray): ByteArray? {
        if (!loaded) return null
        return runCatching { nativeParseAuthSessionInfo(response) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    fun parseAuthConfirmationOrNull(response: ByteArray): ByteArray? {
        if (!loaded) return null
        return runCatching { nativeParseAuthConfirmation(response) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    fun parseAuthorizedDevicesOrNull(response: ByteArray): ByteArray? {
        if (!loaded || response.isEmpty()) return null
        return runCatching { nativeParseAuthorizedDevices(response) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    fun parseTradeOffersOrNull(response: ByteArray): ByteArray? {
        if (!loaded || response.isEmpty()) return null
        return runCatching { nativeParseTradeOffers(response) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    fun parseWishlistOrNull(response: ByteArray): ByteArray? {
        if (!loaded || response.isEmpty()) return null
        return runCatching { nativeParseWishlist(response) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    fun parseMaFileJsonOrNull(
        plainJson: String,
        fileName: String?,
        displayNameOverride: String?,
        steamIdOverride: String?,
        allowMissingSteamId: Boolean
    ): ByteArray? {
        if (!loaded || plainJson.isEmpty()) return null
        return runCatching {
            nativeParseMaFileJson(
                plainJson = plainJson,
                fileName = fileName.orEmpty(),
                displayNameOverride = displayNameOverride.orEmpty(),
                steamIdOverride = steamIdOverride.orEmpty(),
                allowMissingSteamId = allowMissingSteamId
            )
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun buildLoginBeginCredentialsOrNull(
        deviceFriendlyName: String,
        userName: String,
        encryptedPassword: String,
        encryptionTimestamp: String,
        platformType: Long,
        osType: Long,
        gamingDeviceType: Long,
        websiteId: String
    ): ByteArray? {
        if (!loaded) return null
        return runCatching {
            nativeBuildLoginBeginCredentials(
                deviceFriendlyName,
                userName,
                encryptedPassword,
                encryptionTimestamp,
                platformType,
                osType,
                gamingDeviceType,
                websiteId
            )
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun parseLoginBeginCredentialsOrNull(response: ByteArray): ByteArray? {
        if (!loaded || response.isEmpty()) return null
        return runCatching { nativeParseLoginBeginCredentials(response) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    fun buildLoginBeginQrOrNull(
        deviceFriendlyName: String,
        platformType: Long,
        osType: Long,
        gamingDeviceType: Long,
        websiteId: String
    ): ByteArray? {
        if (!loaded) return null
        return runCatching {
            nativeBuildLoginBeginQr(
                deviceFriendlyName,
                platformType,
                osType,
                gamingDeviceType,
                websiteId
            )
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun parseLoginBeginQrOrNull(response: ByteArray): ByteArray? {
        if (!loaded || response.isEmpty()) return null
        return runCatching { nativeParseLoginBeginQr(response) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    fun buildLoginUpdateGuardOrNull(
        clientId: String,
        steamId: String,
        code: String,
        confirmationType: Int
    ): ByteArray? {
        if (!loaded) return null
        return runCatching {
            nativeBuildLoginUpdateGuard(clientId, steamId, code, confirmationType)
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun buildLoginPollOrNull(
        clientId: String,
        requestId: String,
        tokenToRevoke: String? = null
    ): ByteArray? {
        if (!loaded) return null
        return runCatching {
            nativeBuildLoginPoll(clientId, requestId, tokenToRevoke.orEmpty())
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun parseLoginPollOrNull(response: ByteArray): ByteArray? {
        if (!loaded || response.isEmpty()) return null
        return runCatching { nativeParseLoginPoll(response) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    fun buildLoginAccessTokenOrNull(refreshToken: String, steamId: String): ByteArray? {
        if (!loaded) return null
        return runCatching { nativeBuildLoginAccessToken(refreshToken, steamId) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    fun parseLoginAccessTokenOrNull(response: ByteArray): ByteArray? {
        if (!loaded || response.isEmpty()) return null
        return runCatching { nativeParseLoginAccessToken(response) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    fun buildAddAuthenticatorOrNull(steamId: String, authTime: Long, deviceId: String): ByteArray? {
        if (!loaded) return null
        return runCatching { nativeBuildAddAuthenticator(steamId, authTime, deviceId) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    fun parseAddAuthenticatorOrNull(response: ByteArray): ByteArray? {
        if (!loaded || response.isEmpty()) return null
        return runCatching { nativeParseAddAuthenticator(response) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    fun buildFinalizeAuthenticatorOrNull(
        steamId: String,
        authenticatorCode: String,
        authTime: Long,
        activationCode: String,
        validateSmsCode: Boolean
    ): ByteArray? {
        if (!loaded) return null
        return runCatching {
            nativeBuildFinalizeAuthenticator(
                steamId,
                authenticatorCode,
                authTime,
                activationCode,
                validateSmsCode
            )
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun parseFinalizeAuthenticatorOrNull(response: ByteArray): ByteArray? {
        if (!loaded || response.isEmpty()) return null
        return runCatching { nativeParseFinalizeAuthenticator(response) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    fun buildReplaceAuthenticatorContinueOrNull(code: String): ByteArray? {
        if (!loaded) return null
        return runCatching { nativeBuildReplaceAuthenticatorContinue(code) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    fun parseReplaceAuthenticatorContinueOrNull(response: ByteArray): ByteArray? {
        if (!loaded || response.isEmpty()) return null
        return runCatching { nativeParseReplaceAuthenticatorContinue(response) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    fun parseGroupChatHistoryOrNull(response: ByteArray): ByteArray? {
        if (!loaded || response.isEmpty()) return null
        return runCatching { nativeParseGroupChatHistory(response) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    fun parseGroupChatSummariesOrNull(response: ByteArray): ByteArray? {
        if (!loaded || response.isEmpty()) return null
        return runCatching { nativeParseGroupChatSummaries(response) }
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
    private external fun nativeGenerateAuthCode(sharedSecretBase64: String, unixTimeSeconds: Long): String

    @JvmStatic
    private external fun nativeGenerateConfirmationHash(identitySecretBase64: String, unixTimeSeconds: Long, tag: String): String

    @JvmStatic
    private external fun nativeGenerateLoginApprovalSignature(sharedSecretBase64: String, version: Int, clientId: Long, steamId: Long): ByteArray

    @JvmStatic
    private external fun nativeGenerateLoginTokenSignature(sharedSecretBase64: String, tokenId: Long): ByteArray

    @JvmStatic
    private external fun nativeEncodeCmMessage(eMsg: Int, steamId: Long, sessionId: Int, body: ByteArray, jobIdSource: Long, jobIdTarget: Long, targetJobName: String): ByteArray

    @JvmStatic private external fun nativeDecodeCmMessages(payload: ByteArray): ByteArray
    @JvmStatic private external fun nativeParseChatSessions(response: ByteArray): ByteArray
    @JvmStatic private external fun nativeParseChatMessages(response: ByteArray): ByteArray
    @JvmStatic private external fun nativeParseOwnedGames(response: ByteArray): ByteArray
    @JvmStatic private external fun nativeParseAchievementProgress(response: ByteArray): ByteArray
    @JvmStatic private external fun nativeParseStoreItems(response: ByteArray): ByteArray
    @JvmStatic private external fun nativeParseAchievementDetails(definitionsResponse: ByteArray, userResponse: ByteArray): ByteArray
    @JvmStatic private external fun nativeParseFamilySharedApps(response: ByteArray): ByteArray
    @JvmStatic private external fun nativeParseFriendNicknames(response: ByteArray): ByteArray
    @JvmStatic private external fun nativeParsePendingLoginClientIds(response: ByteArray): ByteArray
    @JvmStatic private external fun nativeParseAuthSessionInfo(response: ByteArray): ByteArray
    @JvmStatic private external fun nativeParseAuthConfirmation(response: ByteArray): ByteArray
    @JvmStatic private external fun nativeParseAuthorizedDevices(response: ByteArray): ByteArray
    @JvmStatic private external fun nativeParseTradeOffers(response: ByteArray): ByteArray
    @JvmStatic private external fun nativeParseWishlist(response: ByteArray): ByteArray
    @JvmStatic private external fun nativeParseMaFileJson(plainJson: String, fileName: String, displayNameOverride: String, steamIdOverride: String, allowMissingSteamId: Boolean): ByteArray
    @JvmStatic private external fun nativeBuildLoginBeginCredentials(deviceFriendlyName: String, userName: String, encryptedPassword: String, encryptionTimestamp: String, platformType: Long, osType: Long, gamingDeviceType: Long, websiteId: String): ByteArray
    @JvmStatic private external fun nativeParseLoginBeginCredentials(response: ByteArray): ByteArray
    @JvmStatic private external fun nativeBuildLoginBeginQr(deviceFriendlyName: String, platformType: Long, osType: Long, gamingDeviceType: Long, websiteId: String): ByteArray
    @JvmStatic private external fun nativeParseLoginBeginQr(response: ByteArray): ByteArray
    @JvmStatic private external fun nativeBuildLoginUpdateGuard(clientId: String, steamId: String, code: String, confirmationType: Int): ByteArray
    @JvmStatic private external fun nativeBuildLoginPoll(clientId: String, requestId: String, tokenToRevoke: String): ByteArray
    @JvmStatic private external fun nativeParseLoginPoll(response: ByteArray): ByteArray
    @JvmStatic private external fun nativeBuildLoginAccessToken(refreshToken: String, steamId: String): ByteArray
    @JvmStatic private external fun nativeParseLoginAccessToken(response: ByteArray): ByteArray
    @JvmStatic private external fun nativeBuildAddAuthenticator(steamId: String, authTime: Long, deviceId: String): ByteArray
    @JvmStatic private external fun nativeParseAddAuthenticator(response: ByteArray): ByteArray
    @JvmStatic private external fun nativeBuildFinalizeAuthenticator(steamId: String, authenticatorCode: String, authTime: Long, activationCode: String, validateSmsCode: Boolean): ByteArray
    @JvmStatic private external fun nativeParseFinalizeAuthenticator(response: ByteArray): ByteArray
    @JvmStatic private external fun nativeBuildReplaceAuthenticatorContinue(code: String): ByteArray
    @JvmStatic private external fun nativeParseReplaceAuthenticatorContinue(response: ByteArray): ByteArray
    @JvmStatic private external fun nativeParseGroupChatHistory(response: ByteArray): ByteArray
    @JvmStatic private external fun nativeParseGroupChatSummaries(response: ByteArray): ByteArray
    @JvmStatic private external fun nativeWebLogonBody(webLogonToken: String): ByteArray
}
