package com.hearyet.app.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hearyet.app.settings.screens.general.GeneralPreferencesScreen
import kotlinx.serialization.Serializable

@Serializable
object GeneralPreferencesRoute : NavKey

fun NavBackStack<NavKey>.navigateToGeneralPreferences() {
    add(GeneralPreferencesRoute)
}

fun EntryProviderScope<NavKey>.generalPreferencesEntry(onNavigateUp: () -> Unit) {
    entry<GeneralPreferencesRoute> {
        GeneralPreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
