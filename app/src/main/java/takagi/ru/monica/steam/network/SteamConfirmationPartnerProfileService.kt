package takagi.ru.monica.steam.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import takagi.ru.monica.steam.data.SteamAccount

/** Lightweight profile lookup used by the confirmation detail screen. */
data class SteamConfirmationPartnerProfile(
    val steamId: String,
    val personaName: String,
    val avatarUrl: String,
    val steamLevel: Int?,
    val timeCreated: Long
)

class SteamConfirmationPartnerProfileService(
    private val api: SteamApiClient = SteamApiClient()
) {
    fun fetch(
        account: SteamAccount,
        partnerSteamId: String
    ): SteamConfirmationPartnerProfile? {
        val normalizedSteamId = partnerSteamId.trim()
        if (!normalizedSteamId.matches(STEAM_ID64_PATTERN)) return null
        val accessToken = account.accessToken?.takeIf(String::isNotBlank) ?: return null
        val payload = api.steamApiGetJson(
            path = "/ISteamUserOAuth/GetUserSummaries/v1/",
            query = mapOf("steamids" to normalizedSteamId),
            accessToken = accessToken
        )
        val player = payload.obj("response")
            ?.array("players")
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it.string("steamid") == normalizedSteamId }
            ?: return null
        val steamLevel = runCatching {
            SteamProtoReader(
                api.callProtobuf(
                    iface = "IPlayerService",
                    method = "GetSteamLevel",
                    request = SteamProtoWriter().apply {
                        writeUint64(1, normalizedSteamId.toLong())
                    },
                    accessToken = accessToken,
                    useGet = true
                )
            ).parse()[1]?.asLong?.toInt()?.takeIf { it >= 0 }
        }.getOrNull()
        return SteamConfirmationPartnerProfile(
            steamId = normalizedSteamId,
            personaName = player.string("personaname").ifBlank { normalizedSteamId },
            avatarUrl = player.string("avatarfull")
                .ifBlank { player.string("avatarmedium") },
            steamLevel = steamLevel,
            timeCreated = player.long("timecreated")
        )
    }

    private companion object {
        val STEAM_ID64_PATTERN = Regex("7656119\\d{10}")

        private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

        private fun JsonObject.array(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())

        private fun JsonObject.string(key: String): String =
            (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

        private fun JsonObject.long(key: String): Long = string(key).toLongOrNull() ?: 0L
    }
}
