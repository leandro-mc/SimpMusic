package com.maxrave.simpmusic.floatinglyrics

import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import android.view.View

/**
 * A `ComposeView` normally gets its [LifecycleOwner] / [ViewModelStoreOwner] /
 * [SavedStateRegistryOwner] for free from the hosting `Activity`. A window added directly to the
 * `WindowManager` from a `Service` has none of those — Compose throws immediately without them.
 *
 * This is the standard, minimal trio a Service needs to hand-roll to host Compose. No back-press
 * dispatcher owner is provided: nothing in the floating window uses one (no modal sheets / system
 * back handling inside the bubble).
 */
class FloatingLyricsLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore = ViewModelStore()

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun attachToView(view: View) {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun onWindowShown() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun onWindowHidden() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

/** Applies [ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed], the correct strategy
 *  now that the view tree has a real (Service-owned) lifecycle attached above. */
internal fun disposeComposeOnLifecycleDestroyed(composeView: androidx.compose.ui.platform.AbstractComposeView) {
    composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
}
