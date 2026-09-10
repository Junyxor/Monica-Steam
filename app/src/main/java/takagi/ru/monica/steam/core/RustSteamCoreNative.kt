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
    @JvmStatic private external fun nativeParseGroupChatHistory(response: ByteArray): ByteArray
    @JvmStatic private external fun nativeParseGroupChatSummaries(response: ByteArray): ByteArray
    @JvmStatic private external fun nativeWebLogonBody(webLogonToken: String): ByteArray
}
