package takagi.ru.monica.steam.library.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import takagi.ru.monica.steam.library.SteamAchievement
import takagi.ru.monica.steam.library.SteamAchievementSyncPlan
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.SteamGameAchievements
import takagi.ru.monica.steam.library.SteamLibrarySnapshot

class SteamAchievementSyncModelsTest {
    @Test
    fun forceFullSyncReplacesAStuckCheckpointWithANewRequest() {
        val snapshot = SteamLibrarySnapshot(
            accountId = 7L,
            games = listOf(game(10), game(20)),
            fetchedAt = 1L,
            achievementSyncPlan = SteamAchievementSyncPlan(
                requestId = "stuck-request",
                pendingAppIds = listOf(20),
                retryAppIds = listOf(10),
                failedAppIds = listOf(10),
                completedGames = 1,
                totalGames = 2,
                isFullSync = true,
                startedAt = 2L
            )
        )

        val restarted = snapshot.prepareAchievementSyncPlan(
            requestId = "new-request",
            forceFull = true,
            nowMillis = 3L
        )

        assertEquals("new-request", restarted.achievementSyncPlan?.requestId)
        assertEquals(listOf(10, 20), restarted.achievementSyncPlan?.pendingAppIds)
        assertEquals(emptyList<Int>(), restarted.achievementSyncPlan?.retryAppIds)
        assertEquals(emptyList<Int>(), restarted.achievementSyncPlan?.failedAppIds)
        assertEquals(0, restarted.achievementSyncPlan?.completedGames)
    }

    @Test
    fun missingAchievementGamesAreOrderedByRecentActivity() {
        val snapshot = SteamLibrarySnapshot(
            accountId = 7L,
            games = listOf(
                game(appId = 10, recent = 0, lastPlayedAt = 10L),
                game(appId = 20, recent = 45, lastPlayedAt = 20L),
                game(appId = 30, recent = 5, lastPlayedAt = 30L)
            ),
            fetchedAt = 1L
        )

        val selection = selectSteamAchievementSyncGames(snapshot, forceFull = false)

        assertEquals(listOf(20, 30, 10), selection.games.map(SteamGame::appId))
        assertEquals(true, selection.isFullSync)
    }

    @Test
    fun achievementCheckpointUpdatesOnlyTheCompletedGame() {
        val snapshot = SteamLibrarySnapshot(
            accountId = 7L,
            games = listOf(game(10), game(20)),
            fetchedAt = 1L
        )
        val details = achievements(appId = 20, unlocked = 2, total = 3)

        val updated = snapshot.withAchievementCheckpoint(details)

        assertNull(updated.games.first().achievementTotalCount)
        assertEquals(2, updated.games.last().achievementUnlockedCount)
        assertEquals(3, updated.games.last().achievementTotalCount)
        assertEquals(120, updated.games.last().achievementProgressPlaytimeMinutes)
    }

    private fun game(
        appId: Int,
        recent: Int = 0,
        lastPlayedAt: Long = 0L
    ) = SteamGame(
        appId = appId,
        name = "Game $appId",
        playtimeForeverMinutes = 120,
        playtimeRecentMinutes = recent,
        lastPlayedAt = lastPlayedAt
    )

    private fun achievements(
        appId: Int,
        unlocked: Int,
        total: Int
    ) = SteamGameAchievements(
        accountId = 7L,
        appId = appId,
        gameName = "Game $appId",
        achievements = (0 until total).map { index ->
            SteamAchievement(
                apiName = "ACH_$index",
                displayName = "Achievement $index",
                description = "",
                achieved = index < unlocked,
                unlockTimeSeconds = null,
                iconUrl = null,
                lockedIconUrl = null
            )
        },
        fetchedAt = 2L
    )
}
