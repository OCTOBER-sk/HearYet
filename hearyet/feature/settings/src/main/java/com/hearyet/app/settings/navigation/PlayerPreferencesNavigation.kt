package com.hearyet.app.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hearyet.app.settings.screens.player.PlayerPreferencesScreen
import kotlinx.serialization.Serializable

@Serializable
object PlayerPreferencesRoute : NavKey

fun NavBackStack<NavKey>.navigateToPlayerPreferences() {
    add(PlayerPreferencesRoute)
}

fun EntryProviderScope<NavKey>.playerPreferencesEntry(onNavigateUp: () -> Unit) {
    entry<PlayerPreferencesRoute> {
        PlayerPreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
