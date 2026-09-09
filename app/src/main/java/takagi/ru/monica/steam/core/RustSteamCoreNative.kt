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

    fun secondsRemainingOrNull(unixTimeSeconds: Long): Int? {
        if (!loaded) return null
        return runCatching { nativeSecondsRemaining(unixTimeSeconds) }.getOrNull()
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
    private external fun nativeSecondsRemaining(unixTimeSeconds: Long): Int
}
