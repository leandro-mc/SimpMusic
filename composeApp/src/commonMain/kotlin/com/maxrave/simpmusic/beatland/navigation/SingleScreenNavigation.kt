package com.maxrave.simpmusic.beatland.navigation

import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute

/**
 * Beatland fork fix (D-052): a navigation control must not open the screen the user is already on.
 *
 * WHY: `NavController.navigate(route)` always pushes a new entry, even when the same screen is on top.
 * The sidebar rows, the tab buttons and the global buttons (settings, notifications...) stay visible
 * or can be clicked again, so clicking Favorites five times left five copies of Favorites in the back
 * stack and Back walked through all of them; a double click on a mouse did the same.
 */

/**
 * True when the screen on top of the back stack is exactly [route]: the same destination with the same
 * arguments. A different playlist is not "the same screen", so it still opens.
 */
internal fun NavController.isShowing(route: Any): Boolean {
    val entry = currentBackStackEntry ?: return false
    if (!entry.destination.hasRoute(route::class)) return false
    return try {
        entry.toRoute<Any>(route::class) == route
    } catch (e: Exception) {
        // Arguments that cannot be read back: say "not showing" and let the caller navigate as before.
        false
    }
}

/**
 * Opens [route] unless that very screen is already showing. Everything else is exactly
 * `navigate(route)`, so going from one playlist to another still stacks and Back still returns.
 */
internal fun NavController.navigateOnce(route: Any) {
    if (isShowing(route)) return
    navigate(route)
}

/**
 * A tab button was pressed while that tab is already the selected one but a screen opened from it is
 * on top (Favorites opened from Home, a playlist opened from Library...). Goes back to the tab itself
 * instead of stacking a second copy of it: Back no longer returns to the screens that were left.
 */
internal fun NavController.returnToTab(destination: Any) {
    // The tab's own entry is still in the stack, under the screens opened from it: drop those.
    if (popBackStack(destination::class, inclusive = false)) return
    // It is not in the stack (the tab was left some other way): open it the way a tab is opened.
    navigate(destination) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
