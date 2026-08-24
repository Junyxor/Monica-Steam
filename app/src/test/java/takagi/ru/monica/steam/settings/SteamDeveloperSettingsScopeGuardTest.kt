package takagi.ru.monica.steam.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamDeveloperSettingsScopeGuardTest {
    @Test
    fun developerPageContainsAchievementSyncCardVisibilityControlAndThreeLogActions() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/DeveloperSettingsScreen.kt"
        ).readText()
        val page = source
            .substringAfter("fun DeveloperSettingsScreen(")
            .substringBefore("fun DebugLogsDialog(")

        assertEquals(3, Regex("\\bSettingsItem\\(").findAll(page).count())
        assertEquals(1, Regex("\\bSettingsItemWithSwitch\\(").findAll(page).count())
        assertTrue(page.contains("R.string.developer_functions"))
        assertTrue(page.contains("R.string.developer_show_achievement_sync_card"))
        assertTrue(page.contains("R.string.developer_show_achievement_sync_card_desc"))
        assertTrue(page.contains("settings.showAchievementSyncCard"))
        assertTrue(page.contains("settingsManager.updateShowAchievementSyncCard"))
        assertFalse(page.contains("onNavigateToMdbx"))
        assertFalse(page.contains("AutofillPickerActivityV2"))
        assertFalse(page.contains("R.string.disable_password_verification"))
    }

    @Test
    fun achievementSyncCardVisibilityDefaultsToDisabledAndIsPersisted() {
        val appSettings = projectFile(
            "app/src/main/java/takagi/ru/monica/data/AppSettings.kt"
        ).readText()
        val settingsManager = projectFile(
            "app/src/main/java/takagi/ru/monica/utils/SettingsManager.kt"
        ).readText()

        assertTrue(appSettings.contains("val showAchievementSyncCard: Boolean = false"))
        assertTrue(
            settingsManager.contains(
                "SHOW_ACHIEVEMENT_SYNC_CARD_KEY = booleanPreferencesKey(\"show_achievement_sync_card\")"
            )
        )
        assertTrue(
            settingsManager.contains(
                "showAchievementSyncCard = preferences[SHOW_ACHIEVEMENT_SYNC_CARD_KEY] ?: false"
            )
        )
        assertTrue(settingsManager.contains("suspend fun updateShowAchievementSyncCard(enabled: Boolean)"))
        assertTrue(
            settingsManager.contains("preferences[SHOW_ACHIEVEMENT_SYNC_CARD_KEY] = enabled")
        )
    }

    @Test
    fun visibilitySettingOnlyGuardsAchievementSyncStatusCard() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryScreen.kt"
        ).readText()
        val syncMenu = source
            .substringAfter("additionalActions = listOf(")
            .substringBefore("private fun SteamLibraryOverview(")
        val syncCard = source
            .substringAfter("private fun SteamLibraryOverview(")
            .substringBefore("private fun SteamAccountHeroSwitcher(")

        assertTrue(syncMenu.contains("viewModel.syncAllAchievementProgress()"))
        assertFalse(syncMenu.contains("showAchievementSyncCard"))
        assertTrue(syncCard.contains("appSettings.showAchievementSyncCard"))
        assertTrue(syncCard.contains("achievement_progress_sync_status"))
        assertEquals(1, Regex("\\bshowAchievementSyncCard\\b").findAll(source).count())
    }

    @Test
    fun passwordVerificationControlBelongsToMasterPasswordAndLocking() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MasterPasswordLockingSettingsScreen.kt"
        ).readText()

        assertTrue(source.contains("R.string.disable_password_verification"))
        assertTrue(source.contains("settings.disablePasswordVerification"))
        assertTrue(source.contains("viewModel.updateDisablePasswordVerification"))
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
