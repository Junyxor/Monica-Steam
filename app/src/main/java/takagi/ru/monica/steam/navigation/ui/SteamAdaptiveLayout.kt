package takagi.ru.monica.steam.navigation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

/** Window-size policy shared by Steam screens and all three dock styles. */
enum class SteamAdaptiveLayoutMode {
    COMPACT,
    LANDSCAPE,
    EXPANDED
}

@Immutable
data class SteamAdaptiveLayoutInfo(
    val mode: SteamAdaptiveLayoutMode,
    val useNavigationRail: Boolean,
    val useTwoPaneLayout: Boolean,
    val preferredListPaneWidthDp: Int
)

internal fun resolveSteamAdaptiveLayout(
    widthDp: Int,
    heightDp: Int
): SteamAdaptiveLayoutInfo {
    val shortestSide = minOf(widthDp, heightDp)
    val mode = when {
        shortestSide >= 600 -> SteamAdaptiveLayoutMode.EXPANDED
        widthDp >= 600 && heightDp < 600 -> SteamAdaptiveLayoutMode.LANDSCAPE
        else -> SteamAdaptiveLayoutMode.COMPACT
    }
    return SteamAdaptiveLayoutInfo(
        mode = mode,
        useNavigationRail = mode != SteamAdaptiveLayoutMode.COMPACT,
        useTwoPaneLayout = mode != SteamAdaptiveLayoutMode.COMPACT,
        preferredListPaneWidthDp = if (mode == SteamAdaptiveLayoutMode.EXPANDED) 400 else 320
    )
}

@Composable
internal fun rememberSteamAdaptiveLayout(): SteamAdaptiveLayoutInfo {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        resolveSteamAdaptiveLayout(
            widthDp = configuration.screenWidthDp,
            heightDp = configuration.screenHeightDp
        )
    }
}
