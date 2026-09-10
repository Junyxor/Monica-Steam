from __future__ import annotations

from pathlib import Path

SERVICE = Path("app/src/main/java/takagi/ru/monica/steam/token/data/SteamLoginImportService.kt")
GUARD = Path("app/src/test/java/takagi/ru/monica/steam/SteamLoginImportServiceGuardTest.kt")


def replace_region(text: str, start: str, end: str, replacement: str) -> str:
    if text.count(start) != 1:
        raise RuntimeError(f"expected one start marker: {start!r}, got {text.count(start)}")
    start_index = text.index(start)
    end_index = text.find(end, start_index + len(start))
    if end_index < 0:
        raise RuntimeError(f"missing end marker after {start!r}: {end!r}")
    return text[:start_index] + replacement.rstrip() + "\n\n" + text[end_index:]


def replace_exact(text: str, old: str, new: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"expected one exact match, got {count}: {old[:120]!r}")
    return text.replace(old, new, 1)


service = SERVICE.read_text(encoding="utf-8")

service = replace_exact(service, "import android.util.Base64\n", "")
service = replace_exact(
    service,
    """        private val UNSIGNED_LONG_MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)\n        private val SIGNED_LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE)\n        private val UNSIGNED_LONG_BASE = BigInteger.ONE.shiftLeft(64)\n\n""",
    "",
)
service = replace_exact(
    service,
    """        val signedTokenId = parseUnsigned64AsSignedLong(tokenId)\n            ?: return@withContext AuthorizedDeviceRevokeResult.Failure(\n                \"Steam authorized-device token is invalid\"\n            )""",
    """        val signedTokenId = tokenId.trim().toULongOrNull()?.toLong()\n            ?: return@withContext AuthorizedDeviceRevokeResult.Failure(\n                \"Steam authorized-device token is invalid\"\n            )""",
)

service = replace_region(
    service,
    "    private data class AuthApiSessionIds(",
    "    private data class BeginAuthSessionData(",
    "",
)

service = replace_region(
    service,
    "    private fun beginAuthSessionViaCredentialsWithProtobuf(",
    "    private fun submitSteamGuardCodeWithForm(",
    r'''    private fun beginAuthSessionViaCredentialsWithProtobuf(
        userName: String,
        encryptedPassword: String,
        encryptionTimestamp: String,
        throwApiErrors: Boolean = false
    ): BeginAuthSessionData? {
        val request = SteamProtoWriter.fromEncoded(
            SteamLoginAuthProtocol.buildBeginCredentialsRequest(
                userName = userName,
                encryptedPassword = encryptedPassword,
                encryptionTimestamp = encryptionTimestamp
            )
        )

        val response = try {
            steamApi.callProtobuf(
                iface = "IAuthenticationService",
                method = "BeginAuthSessionViaCredentials",
                request = request
            )
        } catch (error: SteamApiException) {
            safeLogWarning(
                "BeginAuthSessionViaCredentials protobuf failed: " +
                    "eResult=${error.eResult}, message=${error.message}",
                error
            )
            if (
                !throwApiErrors &&
                SteamLoginErrorPolicy.shouldFallbackToMobileForm(
                    eResult = error.eResult,
                    httpStatusCode = error.httpStatusCode
                )
            ) {
                return null
            }
            throw error
        } catch (error: Exception) {
            safeLogWarning(
                "BeginAuthSessionViaCredentials protobuf exception: ${error.message}",
                error
            )
            throw error
        }

        val parsed = SteamLoginAuthProtocol.parseBeginCredentialsResponse(response)
        if (parsed == null) {
            safeLogWarning("BeginAuthSessionViaCredentials protobuf returned an incomplete payload")
            throw IllegalStateException("Steam returned an incomplete authentication response")
        }
        return BeginAuthSessionData(
            clientId = parsed.clientId,
            requestId = parsed.requestId,
            steamId = parsed.steamId,
            challenges = parsed.challenges.map { challenge ->
                SteamGuardChallenge(
                    confirmationType = challenge.confirmationType,
                    associatedMessage = challenge.associatedMessage
                )
            },
            message = parsed.message
        )
    }

    private fun beginAuthSessionViaQrWithProtobuf(): BeginQrAuthSessionData? {
        val request = SteamProtoWriter.fromEncoded(
            SteamLoginAuthProtocol.buildBeginQrRequest()
        )
        val response = try {
            steamApi.callProtobuf(
                iface = "IAuthenticationService",
                method = "BeginAuthSessionViaQR",
                request = request
            )
        } catch (error: SteamApiException) {
            android.util.Log.w(
                TAG,
                "BeginAuthSessionViaQR protobuf failed: eResult=${error.eResult}, message=${error.message}"
            )
            return null
        } catch (error: Exception) {
            android.util.Log.w(
                TAG,
                "BeginAuthSessionViaQR protobuf exception: ${error.message}",
                error
            )
            return null
        }

        val parsed = SteamLoginAuthProtocol.parseBeginQrResponse(response)
        if (parsed == null) {
            android.util.Log.w(TAG, "BeginAuthSessionViaQR protobuf returned an incomplete payload")
            return null
        }
        return BeginQrAuthSessionData(
            clientId = parsed.clientId,
            requestId = parsed.requestId,
            challengeUrl = parsed.challengeUrl,
            challenges = parsed.challenges.map { challenge ->
                SteamGuardChallenge(
                    confirmationType = challenge.confirmationType,
                    associatedMessage = challenge.associatedMessage
                )
            }
        )
    }

    private fun submitSteamGuardCodeWithProtobuf(
        session: PendingAuthSession,
        code: String,
        confirmationType: Int
    ): SteamGuardSubmitResult {
        val requestBytes = SteamLoginAuthProtocol.buildUpdateGuardRequest(
            clientId = session.clientId,
            steamId = session.steamId,
            code = code,
            confirmationType = confirmationType
        ) ?: return SteamGuardSubmitResult.UnsupportedSession
        val request = SteamProtoWriter.fromEncoded(requestBytes)

        return try {
            steamApi.callProtobuf(
                iface = "IAuthenticationService",
                method = "UpdateAuthSessionWithSteamGuardCode",
                request = request
            )
            logDiag("submit guard protobuf accepted")
            SteamGuardSubmitResult.Accepted
        } catch (error: SteamApiException) {
            when (error.eResult) {
                29 -> {
                    logDiag("submit guard protobuf duplicate accepted")
                    SteamGuardSubmitResult.Accepted
                }
                9 -> {
                    logDiag("submit guard protobuf unsupported eResult=9; falling back form")
                    SteamGuardSubmitResult.UnsupportedSession
                }
                else -> {
                    logDiag("submit guard protobuf failed eResult=${error.eResult ?: "unknown"}")
                    SteamGuardSubmitResult.Failure(
                        mapEresultToMessage(error.eResult)
                            ?: error.message
                            ?: "Steam 验证失败"
                    )
                }
            }
        } catch (error: Exception) {
            logDiag("submit guard protobuf exception type=${error.javaClass.simpleName}")
            android.util.Log.e(TAG, "submitSteamGuardCodeWithProtobuf failed: ${error.message}", error)
            SteamGuardSubmitResult.Failure(error.message ?: "提交 Steam 验证码失败")
        }
    }''',
)

service = replace_region(
    service,
    "    private suspend fun pollForToken(",
    "    private suspend fun pollForTokenWithForm(",
    r'''    private suspend fun pollForToken(
        clientId: String,
        requestId: String,
        steamId: String,
        maxAttempts: Int = MAX_POLL_ATTEMPTS,
        pendingResult: LoginResult? = null,
        purpose: LoginPurpose = LoginPurpose.IMPORT_AUTHENTICATOR
    ): LoginResult {
        if (clientId.trim().toULongOrNull() != null && requestId.isNotBlank()) {
            return pollForTokenWithProtobuf(
                clientId = clientId,
                requestId = requestId,
                steamId = steamId,
                maxAttempts = maxAttempts,
                pendingResult = pendingResult,
                purpose = purpose
            )
        }
        return pollForTokenWithForm(clientId, requestId, steamId, maxAttempts, pendingResult, purpose)
    }

    private suspend fun pollForAuthorizedDeviceRevocation(
        session: PendingAuthSession,
        tokenToRevoke: Long
    ): TemporaryAuthTokens? {
        var clientId = session.clientId
        repeat(MAX_POLL_ATTEMPTS) { attempt ->
            val requestBytes = SteamLoginAuthProtocol.buildPollRequest(
                clientId = clientId,
                requestId = session.requestId,
                tokenToRevoke = tokenToRevoke.toULong().toString()
            ) ?: return null
            val response = steamApi.callProtobuf(
                iface = "IAuthenticationService",
                method = "PollAuthSessionStatus",
                request = SteamProtoWriter.fromEncoded(requestBytes)
            )
            val parsed = SteamLoginAuthProtocol.parsePollResponse(response) ?: return null
            parsed.clientId?.takeIf { it.isNotBlank() }?.let { clientId = it }
            val refreshToken = parsed.refreshToken
            val accessToken = parsed.accessToken
            if (!accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()) {
                return TemporaryAuthTokens(
                    accessToken = accessToken,
                    refreshToken = refreshToken
                )
            }
            if (attempt < MAX_POLL_ATTEMPTS - 1) delay(POLL_INTERVAL_MS)
        }
        return null
    }

    private suspend fun pollForTokenWithProtobuf(
        clientId: String,
        requestId: String,
        steamId: String,
        maxAttempts: Int,
        pendingResult: LoginResult?,
        purpose: LoginPurpose
    ): LoginResult {
        var currentClientId = clientId
        repeat(maxAttempts) { attempt ->
            val requestBytes = SteamLoginAuthProtocol.buildPollRequest(
                clientId = currentClientId,
                requestId = requestId
            ) ?: return LoginResult.Failure("Steam 登录会话参数无效")
            val parsed = try {
                val response = steamApi.callProtobuf(
                    iface = "IAuthenticationService",
                    method = "PollAuthSessionStatus",
                    request = SteamProtoWriter.fromEncoded(requestBytes)
                )
                SteamLoginAuthProtocol.parsePollResponse(response)
                    ?: return LoginResult.Failure("Steam 登录轮询响应无效")
            } catch (error: SteamApiException) {
                return LoginResult.Failure(
                    mapEresultToMessage(error.eResult)
                        ?: error.message
                        ?: "Steam 登录轮询失败"
                )
            } catch (error: Exception) {
                android.util.Log.e(TAG, "pollForTokenWithProtobuf failed: ${error.message}", error)
                return LoginResult.Failure(error.message ?: "Steam 登录轮询失败")
            }

            parsed.clientId?.takeIf { it.isNotBlank() }?.let { currentClientId = it }
            val accessToken = parsed.accessToken
            val refreshToken = parsed.refreshToken
            val resolvedSteamId = resolveSteamIdFromLoginTokens(steamId, accessToken, refreshToken)
            if (!accessToken.isNullOrBlank()) {
                logDiag("poll protobuf tokens access=true refresh=${!refreshToken.isNullOrBlank()}")
                if (resolvedSteamId.isNullOrBlank()) {
                    return LoginResult.Failure("Steam 登录成功但无法识别 SteamID，无法继续导入")
                }
                val accountName = parsed.accountName ?: resolvedSteamId
                return resolveLoginPayloadAfterToken(
                    steamId = resolvedSteamId,
                    userName = accountName,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    purpose = purpose
                )
            }
            if (!refreshToken.isNullOrBlank()) {
                logDiag("poll protobuf tokens access=false refresh=true; refreshing access token")
                if (resolvedSteamId.isNullOrBlank()) {
                    return LoginResult.Failure("Steam 登录成功但无法识别 SteamID，无法继续导入")
                }
                val refreshedTokens = generateAccessTokenForApp(
                    steamId = resolvedSteamId,
                    refreshToken = refreshToken
                ) ?: return LoginResult.Failure("Steam 登录成功但无法换取 access token，无法继续导入")
                val accountName = parsed.accountName ?: resolvedSteamId
                return resolveLoginPayloadAfterToken(
                    steamId = resolvedSteamId,
                    userName = accountName,
                    accessToken = refreshedTokens.accessToken,
                    refreshToken = refreshedTokens.refreshToken ?: refreshToken,
                    purpose = purpose
                )
            }

            if (attempt < maxAttempts - 1) delay(POLL_INTERVAL_MS)
        }

        return pendingResult ?: LoginResult.Failure("Steam 登录等待超时，请稍后重试")
    }

    private fun pollQrForToken(
        pendingSessionId: String,
        session: PendingAuthSession
    ): QrLoginResult {
        val requestBytes = SteamLoginAuthProtocol.buildPollRequest(
            clientId = session.clientId,
            requestId = session.requestId
        ) ?: return QrLoginResult.Failure("二维码登录会话参数无效，请重新开始")
        val parsed = try {
            val response = steamApi.callProtobuf(
                iface = "IAuthenticationService",
                method = "PollAuthSessionStatus",
                request = SteamProtoWriter.fromEncoded(requestBytes)
            )
            SteamLoginAuthProtocol.parsePollResponse(response)
                ?: return QrLoginResult.Failure("Steam 二维码登录轮询响应无效")
        } catch (error: SteamApiException) {
            return QrLoginResult.Failure(
                mapEresultToMessage(error.eResult)
                    ?: error.message
                    ?: "Steam 二维码登录轮询失败"
            )
        } catch (error: Exception) {
            android.util.Log.e(TAG, "pollQrForToken failed: ${error.message}", error)
            return QrLoginResult.Failure(error.message ?: "Steam 二维码登录轮询失败")
        }

        val currentSession = session.copy(
            clientId = parsed.clientId ?: session.clientId,
            qrChallengeUrl = parsed.challengeUrl ?: session.qrChallengeUrl
        )
        val accessToken = parsed.accessToken
        val refreshToken = parsed.refreshToken
        if (!accessToken.isNullOrBlank() || !refreshToken.isNullOrBlank()) {
            val resolvedSteamId = resolveSteamIdFromLoginTokens(
                currentSession.steamId,
                accessToken,
                refreshToken
            ) ?: return QrLoginResult.Failure("Steam 登录成功但无法识别 SteamID，无法继续导入")
            val refreshedTokens = if (accessToken.isNullOrBlank()) {
                generateAccessTokenForApp(
                    steamId = resolvedSteamId,
                    refreshToken = requireNotNull(refreshToken)
                ) ?: return QrLoginResult.Failure("Steam 登录成功但无法换取 access token，无法继续导入")
            } else {
                null
            }
            val finalAccessToken = accessToken ?: requireNotNull(refreshedTokens).accessToken
            val finalRefreshToken = if (!accessToken.isNullOrBlank()) {
                refreshToken
            } else {
                requireNotNull(refreshedTokens).refreshToken ?: refreshToken
            }
            val accountName = parsed.accountName ?: resolvedSteamId
            pendingSessions.remove(pendingSessionId)
            return when (
                val loginResult = resolveLoginPayloadAfterToken(
                    steamId = resolvedSteamId,
                    userName = accountName,
                    accessToken = finalAccessToken,
                    refreshToken = finalRefreshToken,
                    purpose = currentSession.purpose
                )
            ) {
                is LoginResult.ReadyForImport -> QrLoginResult.ReadyForImport(loginResult)
                is LoginResult.ChallengeRequired -> QrLoginResult.LoginChallengeRequired(loginResult)
                is LoginResult.Failure -> QrLoginResult.Failure(loginResult.message, loginResult.retryable)
            }
        }

        pendingSessions[pendingSessionId] = currentSession
        return QrLoginResult.ChallengeRequired(
            pendingSessionId = pendingSessionId,
            challengeUrl = currentSession.qrChallengeUrl.orEmpty()
        )
    }''',
)

service = replace_region(
    service,
    "    private fun generateAccessTokenForApp(",
    "    private fun resolveSteamIdFromLoginTokens(",
    r'''    private fun generateAccessTokenForApp(
        steamId: String,
        refreshToken: String
    ): AccessTokenRefreshResult? {
        val requestBytes = SteamLoginAuthProtocol.buildGenerateAccessTokenRequest(
            refreshToken = refreshToken,
            steamId = steamId
        ) ?: run {
            logDiag("generate access token skipped steamid_invalid")
            return null
        }

        return try {
            val response = steamApi.callProtobuf(
                iface = "IAuthenticationService",
                method = "GenerateAccessTokenForApp",
                request = SteamProtoWriter.fromEncoded(requestBytes)
            )
            val parsed = SteamLoginAuthProtocol.parseGenerateAccessTokenResponse(response)
            val accessToken = parsed?.accessToken
            val newRefreshToken = parsed?.refreshToken
            val success = !accessToken.isNullOrBlank()
            logDiag("generate access token result success=$success refresh_rotated=${!newRefreshToken.isNullOrBlank()}")
            if (success) {
                AccessTokenRefreshResult(
                    accessToken = requireNotNull(accessToken),
                    refreshToken = newRefreshToken
                )
            } else {
                null
            }
        } catch (error: SteamApiException) {
            logDiag("generate access token failed eResult=${error.eResult ?: "unknown"}")
            android.util.Log.w(TAG, "GenerateAccessTokenForApp failed: eResult=${error.eResult}", error)
            null
        } catch (error: Exception) {
            logDiag("generate access token exception type=${error.javaClass.simpleName}")
            android.util.Log.w(TAG, "GenerateAccessTokenForApp exception: ${error.message}", error)
            null
        }
    }''',
)

service = replace_region(
    service,
    "    private fun buildAuthApiSessionIds(",
    "    private sealed class AddAuthenticatorStartResult",
    "",
)

service = replace_region(
    service,
    "    private fun beginAddAuthenticator(",
    "    private fun finalizeAddAuthenticator(",
    r'''    private fun beginAddAuthenticator(
        steamId: String,
        accountName: String,
        accessToken: String
    ): AddAuthenticatorStartResult {
        val deviceId = generateSteamDeviceId()
        val authTime = System.currentTimeMillis() / 1000L
        val requestBytes = SteamTwoFactorProtocol.buildAddAuthenticatorRequest(
            steamId = steamId,
            authTime = authTime,
            deviceId = deviceId
        ) ?: return AddAuthenticatorStartResult.Failure("SteamID 无效，无法添加 Steam Guard")

        val response = try {
            steamApi.callProtobuf(
                iface = "ITwoFactorService",
                method = "AddAuthenticator",
                request = SteamProtoWriter.fromEncoded(requestBytes),
                accessToken = accessToken
            )
        } catch (error: SteamApiException) {
            logDiag("add authenticator failed eResult=${error.eResult ?: "unknown"}")
            return when (error.eResult) {
                29 -> AddAuthenticatorStartResult.AuthenticatorPresent
                73 -> AddAuthenticatorStartResult.Failure("Steam 账号当前受限，无法添加 Steam Guard")
                84 -> AddAuthenticatorStartResult.Failure("Steam 请求过于频繁，请稍后再试")
                else -> AddAuthenticatorStartResult.Failure(
                    error.message ?: "添加 Steam Guard 失败"
                )
            }
        } catch (error: Exception) {
            logDiag("add authenticator exception type=${error.javaClass.simpleName}")
            android.util.Log.e(TAG, "beginAddAuthenticator failed: ${error.message}", error)
            return AddAuthenticatorStartResult.Failure(error.message ?: "添加 Steam Guard 失败")
        }
        val parsed = SteamTwoFactorProtocol.parseAddAuthenticatorResponse(response)
            ?: return AddAuthenticatorStartResult.Failure("Steam 未返回可解析的令牌数据")

        val status = parsed.status
        logDiag("add authenticator response status=$status")
        if (status == 29) {
            return AddAuthenticatorStartResult.AuthenticatorPresent
        }

        val sharedSecret = parsed.sharedSecret
        if (status == 2 || sharedSecret.isNullOrBlank()) {
            val message = if (status == 2) {
                "该 Steam 账号需要先绑定手机号，才能添加 Steam Guard"
            } else {
                mapTwoFactorStatusToMessage(status)
                    ?: "Steam 未返回完整令牌数据（缺少 shared_secret）"
            }
            return AddAuthenticatorStartResult.Failure(message)
        }

        val serialNumber = parsed.serialNumber
        if (serialNumber.isNullOrBlank()) {
            android.util.Log.w(TAG, "AddAuthenticator missing serial_number")
            logDiag("add authenticator missing serial_number")
            return AddAuthenticatorStartResult.Failure("Steam 未返回完整令牌数据（缺少 serial_number）")
        }

        val confirmType = parsed.confirmType
        val phoneHint = parsed.phoneHint
        val resolvedAccountName = parsed.accountName
            ?: accountName.takeIf { it.isNotBlank() && it != steamId }
        val canonicalPayload = buildJsonObject {
            put("steamid", JsonPrimitive(steamId))
            put("shared_secret", JsonPrimitive(sharedSecret))
            put("serial_number", JsonPrimitive(serialNumber))
            parsed.revocationCode?.let { put("revocation_code", JsonPrimitive(it)) }
            parsed.uri?.let { put("uri", JsonPrimitive(it)) }
            put("server_time", JsonPrimitive((parsed.serverTime ?: authTime).toString()))
            resolvedAccountName?.let { put("account_name", JsonPrimitive(it)) }
            parsed.tokenGid?.let { put("token_gid", JsonPrimitive(it)) }
            parsed.identitySecret?.let { put("identity_secret", JsonPrimitive(it)) }
            parsed.secret1?.let { put("secret_1", JsonPrimitive(it)) }
            put("status", JsonPrimitive(status.toString()))
            put("device_id", JsonPrimitive(deviceId))
            put("fully_enrolled", JsonPrimitive(false))
        }

        android.util.Log.i(
            TAG,
            "AddAuthenticator awaiting finalization: status=$status, confirmType=$confirmType"
        )
        return AddAuthenticatorStartResult.AwaitingFinalization(
            payload = SteamGuardPayload(
                deviceId = deviceId,
                steamGuardJson = canonicalPayload.toString()
            ),
            confirmType = confirmType,
            phoneHint = phoneHint
        )
    }''',
)

service = replace_region(
    service,
    "    private fun finalizeAddAuthenticator(",
    "    private fun SteamGuardPayload.sharedSecretOrNull()",
    r'''    private fun finalizeAddAuthenticator(
        session: PendingAuthSession,
        activationCode: String
    ): LoginResult {
        if (activationCode.isBlank()) {
            return LoginResult.Failure("验证码不能为空", retryable = false)
        }
        val accessToken = session.addAccessToken
            ?: return LoginResult.Failure("Steam Guard 激活会话已过期，请重新登录", retryable = false)
        val payload = session.addPayload
            ?: return LoginResult.Failure("Steam Guard 激活数据已过期，请重新登录", retryable = false)
        if (session.steamId.toLongOrNull() == null) {
            return LoginResult.Failure("SteamID 无效，无法完成 Steam Guard 绑定", retryable = false)
        }
        val sharedSecret = payload.sharedSecretOrNull()
            ?: return LoginResult.Failure("Steam Guard 数据不完整，无法生成激活验证码", retryable = false)

        logDiag("finalize add authenticator start validateSms=${session.addValidateSmsCode}")
        repeat(31) { attempt ->
            val authTime = System.currentTimeMillis() / 1000L
            val authenticatorCode = SteamTotp.generateAuthCode(sharedSecret, authTime)
            val requestBytes = SteamTwoFactorProtocol.buildFinalizeAuthenticatorRequest(
                steamId = session.steamId,
                authenticatorCode = authenticatorCode,
                authTime = authTime,
                activationCode = activationCode,
                validateSmsCode = session.addValidateSmsCode
            ) ?: return LoginResult.Failure("SteamID 无效，无法完成 Steam Guard 绑定", retryable = false)

            val parsed = try {
                val response = steamApi.callProtobuf(
                    iface = "ITwoFactorService",
                    method = "FinalizeAddAuthenticator",
                    request = SteamProtoWriter.fromEncoded(requestBytes),
                    accessToken = accessToken
                )
                SteamTwoFactorProtocol.parseFinalizeAuthenticatorResponse(response)
                    ?: return LoginResult.Failure("Steam Guard 激活响应无效")
            } catch (error: SteamApiException) {
                logDiag("finalize add authenticator failed eResult=${error.eResult ?: "unknown"}")
                return LoginResult.Failure(
                    mapEresultToMessage(error.eResult)
                        ?: error.message
                        ?: "Steam Guard 激活失败"
                )
            } catch (error: Exception) {
                logDiag("finalize add authenticator exception type=${error.javaClass.simpleName}")
                android.util.Log.e(TAG, "finalizeAddAuthenticator failed: ${error.message}", error)
                return LoginResult.Failure(error.message ?: "Steam Guard 激活失败")
            }

            val success = parsed.success
            val wantMore = parsed.wantMore
            val status = parsed.status
            logDiag("finalize add authenticator response success=$success wantMore=$wantMore status=$status attempt=$attempt")
            if (status == 89) {
                return LoginResult.Failure("Steam 激活码无效或已过期")
            }
            if (success) {
                return LoginResult.ReadyForImport(
                    steamId = session.steamId,
                    payload = payload.markFullyEnrolled(),
                    accessToken = session.addImportAccessToken ?: accessToken,
                    refreshToken = session.addRefreshToken
                )
            }
            if (!wantMore && status != 88) {
                return LoginResult.Failure(
                    mapTwoFactorStatusToMessage(status) ?: "Steam Guard 激活失败（status=$status）"
                )
            }
        }

        return LoginResult.Failure("Steam Guard 激活失败：无法生成 Steam 要求的连续验证码")
    }''',
)

service = replace_region(
    service,
    "    private fun continueReplaceAuthenticatorChallenge(",
    "    private fun buildSteamTokenParams(",
    r'''    private fun continueReplaceAuthenticatorChallenge(
        steamId: String,
        accessToken: String,
        code: String
    ): Result<SteamGuardPayload> {
        val request = SteamProtoWriter.fromEncoded(
            SteamTwoFactorProtocol.buildReplaceContinueRequest(code)
        )
        val response = try {
            steamApi.callProtobuf(
                iface = "ITwoFactorService",
                method = "RemoveAuthenticatorViaChallengeContinue",
                request = request,
                accessToken = accessToken
            )
        } catch (error: SteamApiException) {
            logDiag("replace continue failed eResult=${error.eResult ?: "unknown"}")
            return Result.failure(
                Exception(
                    mapEresultToMessage(error.eResult)
                        ?: error.message
                        ?: "替换令牌失败"
                )
            )
        } catch (error: Exception) {
            logDiag("replace continue exception type=${error.javaClass.simpleName}")
            android.util.Log.e(TAG, "continueReplaceAuthenticatorChallenge failed: ${error.message}", error)
            return Result.failure(Exception(error.message ?: "替换令牌失败"))
        }

        val replacement = SteamTwoFactorProtocol.parseReplaceContinueResponse(response)
        val sharedSecret = replacement?.sharedSecret
        val serialNumber = replacement?.serialNumber
        if (sharedSecret.isNullOrBlank() || serialNumber.isNullOrBlank()) {
            android.util.Log.w(TAG, "continueReplaceAuthenticatorChallenge missing token fields")
            logDiag("replace continue missing token fields")
            return Result.failure(
                Exception("替换成功但未返回完整令牌数据（缺少 shared_secret/serial_number）")
            )
        }

        val resolvedDeviceId = generateSteamDeviceId()
        val canonicalPayload = buildJsonObject {
            put("shared_secret", JsonPrimitive(sharedSecret))
            put("serial_number", JsonPrimitive(serialNumber))
            replacement.revocationCode?.let { put("revocation_code", JsonPrimitive(it)) }
            replacement.uri?.let { put("uri", JsonPrimitive(it)) }
            replacement.serverTime?.takeIf { it != 0L }
                ?.let { put("server_time", JsonPrimitive(it.toString())) }
            replacement.accountName?.let { put("account_name", JsonPrimitive(it)) }
            replacement.tokenGid?.let { put("token_gid", JsonPrimitive(it)) }
            replacement.identitySecret?.let { put("identity_secret", JsonPrimitive(it)) }
            replacement.secret1?.let { put("secret_1", JsonPrimitive(it)) }
            replacement.status.takeIf { it != 0 }
                ?.let { put("status", JsonPrimitive(it.toString())) }
            replacement.steamGuardScheme?.takeIf { it != 0 }
                ?.let { put("steamguard_scheme", JsonPrimitive(it.toString())) }
            replacement.steamId?.let { put("steamid", JsonPrimitive(it)) }
            put("device_id", JsonPrimitive(resolvedDeviceId))
            put("fully_enrolled", JsonPrimitive(true))
        }
        logDiag("replace continue success transport=protobuf status=${replacement.status}")
        return Result.success(
            SteamGuardPayload(
                deviceId = resolvedDeviceId,
                steamGuardJson = canonicalPayload.toString()
            )
        )
    }''',
)

SERVICE.write_text(service, encoding="utf-8")

# Keep source-level guard tests asserting the protocol shapes, but point them at
# the new boundaries instead of forcing the giant orchestrator to retain dead
# protobuf code.
guard = GUARD.read_text(encoding="utf-8")

guard = replace_region(
    guard,
    "    @Test\n    fun steamLoginImportKeepsMobileApprovalPollingPath()",
    "    @Test\n    fun steamLoginImportAllowsCodeOrApprovalAndOptionalRemark()",
    r'''    @Test
    fun steamLoginImportKeepsMobileApprovalPollingPath() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/data/SteamLoginImportService.kt"
        ).readText()
        val twoFactorProtocol = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/data/SteamTwoFactorProtocol.kt"
        ).readText()
        val viewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/presentation/SteamViewModel.kt"
        ).readText()

        assertTrue(
            source.contains(
                "\"platform_type\" to SteamMobileAuthRequestProfile.platformType.toString()"
            )
        )
        assertTrue(source.contains("STEAM_WEBSITE_ID = SteamMobileAuthRequestProfile.websiteId"))
        assertTrue(source.contains("pollPendingSession"))
        assertTrue(source.contains("codeAlreadyAccepted = updateEResult == 29"))
        assertTrue(source.contains("method = \"AddAuthenticator\""))
        assertTrue(source.contains("method = \"FinalizeAddAuthenticator\""))
        assertTrue(source.contains("SteamTwoFactorProtocol.buildAddAuthenticatorRequest"))
        assertTrue(twoFactorProtocol.contains("writeFixed64(1, steamIdLong)"))
        assertFalse(source.contains("URL_ADD_AUTHENTICATOR"))
        assertTrue(viewModelSource.contains("startPendingLoginPolling"))
        assertTrue(viewModelSource.contains("SteamLoginImportService.isPollingChallengeType"))
        assertTrue(viewModelSource.contains("SteamLoginImportService.isAddAuthenticatorActivationType"))
    }''',
)

guard = replace_region(
    guard,
    "    @Test\n    fun steamLoginGuardCodeAndPollingUseAuthApiProtobufShape()",
    "    @Test\n    fun steamLoginImportSupportsMonicaGeneratedQrLogin()",
    r'''    @Test
    fun steamLoginGuardCodeAndPollingUseAuthApiProtobufShape() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/data/SteamLoginImportService.kt"
        ).readText()
        val protocolSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/data/SteamLoginAuthProtocol.kt"
        ).readText()
        val errorPolicySource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/loginerror/domain/SteamLoginErrorPolicy.kt"
        ).readText()

        assertTrue(source.contains("beginAuthSessionViaCredentialsWithProtobuf"))
        assertTrue(source.contains("method = \"BeginAuthSessionViaCredentials\""))
        assertTrue(source.contains("SteamLoginAuthProtocol.buildBeginCredentialsRequest"))
        assertTrue(source.contains("SteamLoginAuthProtocol.parseBeginCredentialsResponse"))
        assertTrue(protocolSource.contains("writeString(1, SteamMobileAuthRequestProfile.deviceFriendlyName)"))
        assertTrue(protocolSource.contains("writeString(2, userName)"))
        assertTrue(protocolSource.contains("writeString(3, encryptedPassword)"))
        assertTrue(protocolSource.contains("writeUint64(4, timestamp.toLong())"))
        assertTrue(protocolSource.contains("writeBool(5, false)"))
        assertTrue(protocolSource.contains("writeVarint(6, SteamMobileAuthRequestProfile.platformType)"))
        assertTrue(protocolSource.contains("writeVarint(7, 1L)"))
        assertTrue(protocolSource.contains("writeString(8, SteamMobileAuthRequestProfile.websiteId)"))
        assertTrue(protocolSource.contains("writeMessage(9, buildDeviceDetails())"))
        assertTrue(source.contains("val beginAuthResponse = postForm("))

        assertTrue(source.contains("submitSteamGuardCodeWithProtobuf"))
        assertTrue(source.contains("method = \"UpdateAuthSessionWithSteamGuardCode\""))
        assertTrue(source.contains("SteamLoginAuthProtocol.buildUpdateGuardRequest"))
        assertTrue(protocolSource.contains("writeUint64(1, clientIdBits.toLong())"))
        assertTrue(protocolSource.contains("writeFixed64(2, steamIdBits.toLong())"))
        assertTrue(protocolSource.contains("writeString(3, code.trim())"))
        assertTrue(protocolSource.contains("writeVarint(4, confirmationType.toLong())"))
        assertTrue(source.contains("9 -> {"))
        assertTrue(source.contains("SteamGuardSubmitResult.UnsupportedSession"))
        assertTrue(errorPolicySource.contains("87 -> \"Steam 暂时限制了登录请求"))
        assertTrue(errorPolicySource.contains("88 -> \"Steam 登录失败：令牌验证码错误\""))
        assertTrue(errorPolicySource.contains("fun shouldFallbackToMobileForm"))
        assertTrue(errorPolicySource.contains("fun shouldFallbackToLegacyWeb"))
        assertTrue(source.contains("SteamLoginErrorPolicy.shouldFallbackToMobileForm"))
        assertTrue(source.contains("SteamLoginErrorPolicy.shouldFallbackToLegacyWeb"))
        assertFalse(source.contains("method = \"GetAuthSessionInfo\""))
        assertFalse(source.contains("shouldFallbackCredentialAuth"))
        assertTrue(source.contains("credentialLoginInFlight.compareAndSet(false, true)"))

        assertTrue(source.contains("pollForTokenWithProtobuf"))
        assertTrue(source.contains("method = \"PollAuthSessionStatus\""))
        assertTrue(source.contains("SteamLoginAuthProtocol.buildPollRequest"))
        assertTrue(protocolSource.contains("writeBytes(2, requestIdBytes)"))
        assertTrue(source.contains("generateAccessTokenForApp("))
        assertTrue(source.contains("method = \"GenerateAccessTokenForApp\""))
        assertTrue(source.contains("SteamLoginAuthProtocol.buildGenerateAccessTokenRequest"))
        assertTrue(protocolSource.contains("writeString(1, refreshToken)"))
        assertTrue(protocolSource.contains("writeFixed64(2, steamIdBits.toLong())"))
        assertFalse(source.contains("decodeAuthApiRequestIdBytes"))
        assertFalse(source.contains("parseUnsigned64AsSignedLong"))
        assertTrue(source.contains("pollForTokenWithForm"))
    }''',
)

guard = replace_region(
    guard,
    "    @Test\n    fun steamLoginImportSupportsMonicaGeneratedQrLogin()",
    "    @Test\n    fun steamLoginImportUsesModernAuthenticatorTransferProtobufShape()",
    r'''    @Test
    fun steamLoginImportSupportsMonicaGeneratedQrLogin() {
        val serviceSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/data/SteamLoginImportService.kt"
        ).readText()
        val protocolSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/data/SteamLoginAuthProtocol.kt"
        ).readText()
        val viewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/presentation/SteamViewModel.kt"
        ).readText()
        val screenSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()
        val defaultStrings = projectFile("app/src/main/res/values/strings.xml").readText()
        val zhStrings = projectFile("app/src/main/res/values-zh/strings.xml").readText()

        assertTrue(serviceSource.contains("fun beginQrLogin(sessionOnly: Boolean = false): QrLoginResult"))
        assertTrue(serviceSource.contains("method = \"BeginAuthSessionViaQR\""))
        assertTrue(serviceSource.contains("SteamLoginAuthProtocol.buildBeginQrRequest"))
        assertTrue(serviceSource.contains("SteamLoginAuthProtocol.parseBeginQrResponse"))
        assertTrue(protocolSource.contains("writeString(1, SteamMobileAuthRequestProfile.deviceFriendlyName)"))
        assertTrue(protocolSource.contains("writeVarint(2, 3L)"))
        assertTrue(protocolSource.contains("writeMessage(3, buildDeviceDetails())"))
        assertTrue(protocolSource.contains("writeString(4, SteamMobileAuthRequestProfile.websiteId)"))
        assertTrue(serviceSource.contains("pollQrLoginSession"))
        assertTrue(serviceSource.contains("steamIdFromJwt(refreshToken)"))
        assertTrue(serviceSource.contains("steamIdFromJwt(accessToken)"))
        assertTrue(serviceSource.contains("QrLoginResult.LoginChallengeRequired"))

        assertTrue(viewModelSource.contains("pendingQrLoginChallenge"))
        assertTrue(viewModelSource.contains("fun beginSteamQrLogin("))
        assertTrue(viewModelSource.contains("sessionOnly: Boolean = false"))
        assertTrue(viewModelSource.contains("startPendingQrLoginPolling"))
        assertTrue(viewModelSource.contains("loginImportService.pollQrLoginSession"))
        assertTrue(viewModelSource.contains("handleLoginChallenge(result.challenge)"))

        assertTrue(screenSource.contains("SteamAddAccountMethod.QR_LOGIN"))
        assertTrue(screenSource.contains("viewModel.beginSteamQrLogin(sessionOnly = sessionOnly)"))
        assertTrue(screenSource.contains("SteamQrLoginImportDialog("))
        assertTrue(screenSource.contains("QRCodeWriter().encode(content, BarcodeFormat.QR_CODE"))
        assertTrue(screenSource.contains("R.string.steam_add_method_qr_login"))
        assertTrue(screenSource.contains("R.string.steam_qr_login_import_message"))
        assertTrue(defaultStrings.contains("<string name=\"steam_add_method_qr_login\">"))
        assertTrue(zhStrings.contains("<string name=\"steam_add_method_qr_login\">二维码登录 Steam</string>"))
    }''',
)

guard = replace_region(
    guard,
    "    @Test\n    fun steamLoginImportUsesModernAuthenticatorTransferProtobufShape()",
    "    @Test\n    fun steamLoginImportDoesNotReportTransferStartFailureAsLoginFailure()",
    r'''    @Test
    fun steamLoginImportUsesModernAuthenticatorTransferProtobufShape() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/data/SteamLoginImportService.kt"
        ).readText()
        val protocolSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/data/SteamTwoFactorProtocol.kt"
        ).readText()

        assertTrue(source.contains("method = \"RemoveAuthenticatorViaChallengeStart\""))
        assertTrue(source.contains("method = \"RemoveAuthenticatorViaChallengeContinue\""))
        assertTrue(source.contains("SteamTwoFactorProtocol.buildReplaceContinueRequest"))
        assertTrue(source.contains("SteamTwoFactorProtocol.parseReplaceContinueResponse"))
        assertTrue(protocolSource.contains("writeString(1, code.trim())"))
        assertTrue(protocolSource.contains("writeBool(2, true)"))
        assertTrue(protocolSource.contains("writeVarint(3, 2L)"))
        assertTrue(protocolSource.contains("val replacementFields = fields[2]?.bytes?.let"))
        assertTrue(protocolSource.contains("replacementFields[1]?.bytes"))
        assertTrue(protocolSource.contains("replacementFields[2]?.asFixed64UnsignedString"))
        assertFalse(source.contains("accessToken = session.replaceRefreshToken"))
        assertFalse(source.contains("accessToken = refreshToken"))
    }''',
)

GUARD.write_text(guard, encoding="utf-8")

# Safety checks: the giant orchestrator must no longer own the migrated wire
# helpers, while endpoint orchestration and the compatibility fallbacks remain.
final_service = SERVICE.read_text(encoding="utf-8")
for forbidden in (
    "buildAuthApiDeviceDetails",
    "authApiAllowedConfirmations",
    "decodeAuthApiRequestIdBytes",
    "parseUnsigned64AsSignedLong",
    "unsignedLongToString",
    "val replacementFields = fields[2]?.bytes?.let",
):
    if forbidden in final_service:
        raise RuntimeError(f"legacy wire helper still present: {forbidden}")
for required in (
    "SteamLoginAuthProtocol.buildBeginCredentialsRequest",
    "SteamLoginAuthProtocol.parseBeginCredentialsResponse",
    "SteamLoginAuthProtocol.buildPollRequest",
    "SteamLoginAuthProtocol.parsePollResponse",
    "SteamTwoFactorProtocol.buildAddAuthenticatorRequest",
    "SteamTwoFactorProtocol.parseFinalizeAuthenticatorResponse",
    "SteamTwoFactorProtocol.parseReplaceContinueResponse",
    "method = \"BeginAuthSessionViaCredentials\"",
    "method = \"FinalizeAddAuthenticator\"",
):
    if required not in final_service:
        raise RuntimeError(f"required migrated boundary missing: {required}")

print("Steam login protocol boundary migration applied successfully")
