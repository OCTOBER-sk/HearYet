package com.hearyet.app.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hearyet.app.settings.Setting
import com.hearyet.app.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
object SettingsRoute : NavKey

fun NavBackStack<NavKey>.navigateToSettings() {
    add(SettingsRoute)
}

fun EntryProviderScope<NavKey>.settingsEntry(onNavigateUp: () -> Unit, onItemClick: (Setting) -> Unit) {
    entry<SettingsRoute> {
        SettingsScreen(onNavigateUp = onNavigateUp, onItemClick = onItemClick)
    }
}
