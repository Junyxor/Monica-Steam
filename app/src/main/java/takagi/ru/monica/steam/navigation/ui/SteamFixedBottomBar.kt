package takagi.ru.monica.steam.navigation.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch
import takagi.ru.monica.steam.navigation.SteamDockTab
import takagi.ru.monica.steam.navigation.icon
import takagi.ru.monica.steam.navigation.label

/** Monica Pass-style fixed M3 navigation bar for Steam's top-level pages. */
@Composable
internal fun SteamFixedBottomBar(
    modifier: Modifier = Modifier,
    order: List<SteamDockTab>,
    selected: SteamDockTab,
    onSelected: (SteamDockTab) -> Unit
) {
    val windowInsets = rememberSteamWindowBottomInsets()
    val scope = rememberCoroutineScope()
    val currentOnSelected by rememberUpdatedState(onSelected)
    val selectionGeneration = remember { intArrayOf(0) }
    var optimisticSelected by remember { mutableStateOf(selected) }

    LaunchedEffect(selected) {
        optimisticSelected = selected
    }

    fun selectAfterVisualCommit(tab: SteamDockTab) {
        if (tab == optimisticSelected) return
        optimisticSelected = tab
        val generation = ++selectionGeneration[0]
        scope.launch {
            // Give Material's indicator/ripple one frame before a cold page is composed.
            awaitFrame()
            if (selectionGeneration[0] == generation) {
                currentOnSelected(tab)
            }
        }
    }

    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 0.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        windowInsets = windowInsets
    ) {
        SteamDockTab.completeFixedOrder(order).forEach { tab ->
            val label = tab.label()
            NavigationBarItem(
                selected = optimisticSelected == tab,
                onClick = { selectAfterVisualCommit(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon(),
                        contentDescription = label
                    )
                },
                label = {
                    Text(
                        text = label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                alwaysShowLabel = true
            )
        }
    }
}
