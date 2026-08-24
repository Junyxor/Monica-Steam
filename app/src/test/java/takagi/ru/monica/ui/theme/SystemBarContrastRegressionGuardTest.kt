package takagi.ru.monica.ui.theme

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemBarContrastRegressionGuardTest {
    @Test
    fun activityDisablesSystemNavigationBarContrastScrim() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/base/BaseMonicaActivity.kt"
        ).readText()

        val edgeToEdgeIndex = source.indexOf("enableEdgeToEdge()")
        val systemBarSetupIndex = source.indexOf("configureEdgeToEdgeSystemBars()")
        assertTrue(edgeToEdgeIndex >= 0)
        assertTrue(systemBarSetupIndex > edgeToEdgeIndex)
        assertTrue(source.contains("window.isNavigationBarContrastEnforced = false"))
        assertTrue(source.contains("window.isStatusBarContrastEnforced = false"))
    }

    @Test
    fun composeThemeKeepsNavigationBarTransparentAcrossThemeChanges() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/theme/Theme.kt"
        ).readText()

        assertTrue(
            source.contains(
                "window.navigationBarColor = android.graphics.Color.TRANSPARENT"
            )
        )
        assertTrue(source.contains("isAppearanceLightNavigationBars = !darkTheme"))
        assertTrue(source.contains("window.isNavigationBarContrastEnforced = false"))
    }

    private fun projectFile(relativePath: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, relativePath)
    }
}
