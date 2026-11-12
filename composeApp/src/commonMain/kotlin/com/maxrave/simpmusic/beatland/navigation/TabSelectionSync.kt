package com.maxrave.simpmusic.beatland.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import com.maxrave.simpmusic.ui.component.BottomNavScreen

/**
 * Beatland fork fix (D-050): keeps the highlighted tab of every navigation bar equal to the screen
 * the user is really on.
 *
 * WHY: each bar (phone bar, glass bar, desktop sidebar) keeps its own `selectedIndex` and used to
 * change it ONLY when a tab was tapped. Anything else that moves the user between tabs left it
 * behind: the system back button or gesture (Library → Back → Home, or Search → Back), a deep link,
 * the Quick Settings recognize tile. The bar then lit one tab while another screen was showing.
 *
 * WHAT: whenever the back stack changes, the tab of the top-most entry that belongs to a tab is
 * selected. A sub-screen (a playlist, an album, the player…) is not a tab, so the walk continues
 * down the stack until it finds the tab the sub-screen was opened from; that keeps "open a playlist
 * from Library and Library stays lit" exactly as before, and it also survives the bar leaving and
 * re-entering the composition, which loses the bar's own state. A tab that is not shown at the
 * moment (Mix, Analytics) is skipped.
 *
 * Tapping a tab still selects it at once; this only corrects the bar afterwards when the two differ.
 */
@Composable
internal fun SyncSelectedTab(
    navController: NavController,
    visibleTabs: List<BottomNavScreen>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
) {
    val backStack by navController.currentBackStack.collectAsState()
    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)
    LaunchedEffect(backStack, visibleTabs.map { it.ordinal }) {
        val tab = tabOnTop(backStack, visibleTabs) ?: return@LaunchedEffect
        if (tab.ordinal != currentSelected) currentOnTabSelected(tab.ordinal)
    }
}

/** The tab the user is on: the top-most back stack entry that is, or sits under, one of [tabs]. */
private fun tabOnTop(
    backStack: List<NavBackStackEntry>,
    tabs: List<BottomNavScreen>,
): BottomNavScreen? {
    for (entry in backStack.asReversed()) {
        val tab =
            entry.destination.hierarchy.firstNotNullOfOrNull { destination ->
                tabs.firstOrNull { destination.hasRoute(it.destination::class) }
            }
        if (tab != null) return tab
    }
    return null
}
