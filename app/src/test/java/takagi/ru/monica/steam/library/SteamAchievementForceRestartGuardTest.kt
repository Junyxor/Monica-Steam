package takagi.ru.monica.steam.library

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamAchievementForceRestartGuardTest {
    @Test
    fun activeSyncActionRemainsEnabledAndRestartsTheFullSync() {
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryScreen.kt"
        ).readText()
        val action = screen
            .substringBefore("val started = viewModel.syncAllAchievementProgress()")
            .substringAfterLast("SteamPageOverflowAction(")

        assertTrue(action.contains("R.string.steam_library_force_resync_achievements"))
        assertTrue(action.contains("state.syncingAchievementProgress"))
        assertFalse(action.contains("!state.syncingAchievementProgress"))
        assertTrue(screen.contains("viewModel.syncAllAchievementProgress()"))
        assertTrue(screen.contains("R.string.steam_library_force_resync_started"))
    }

    @Test
    fun forceFullRequestReplacesTheOldWorkerWithoutReadingItsStaleState() {
        val coordinator = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/sync/SteamAchievementSyncCoordinator.kt"
        ).readText()
        val viewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/SteamLibraryViewModel.kt"
        ).readText()
        val schedule = viewModel
            .substringAfter("private fun scheduleAchievementSync(")
            .substringBefore("private fun applyAchievementSyncState(")

        assertTrue(
            coordinator.contains(
                "if (forceFull) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP"
            )
        )
        assertTrue(coordinator.contains("resetProgress = true"))
        assertFalse(schedule.contains("coordinator.refreshState(handle)"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, path)
    }
}
