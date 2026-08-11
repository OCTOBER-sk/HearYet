package com.hearyet.app.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hearyet.app.navigation.OnboardingRoute
import com.hearyet.app.settings.Setting
import com.hearyet.app.settings.navigation.aboutPreferencesEntry
import com.hearyet.app.settings.navigation.audioPreferencesEntry
import com.hearyet.app.settings.navigation.decoderPreferencesEntry
import com.hearyet.app.settings.navigation.folderPreferencesEntry
import com.hearyet.app.settings.navigation.generalPreferencesEntry
import com.hearyet.app.settings.navigation.gesturePreferencesEntry
import com.hearyet.app.settings.navigation.librariesEntry
import com.hearyet.app.settings.navigation.mediaLibraryPreferencesEntry
import com.hearyet.app.settings.navigation.navigateToAboutPreferences
import com.hearyet.app.settings.navigation.navigateToAudioPreferences
import com.hearyet.app.settings.navigation.navigateToDecoderPreferences
import com.hearyet.app.settings.navigation.navigateToFolderPreferencesScreen
import com.hearyet.app.settings.navigation.navigateToGeneralPreferences
import com.hearyet.app.settings.navigation.navigateToGesturePreferences
import com.hearyet.app.settings.navigation.navigateToLibraries
import com.hearyet.app.settings.navigation.navigateToMediaLibraryPreferencesScreen
import com.hearyet.app.settings.navigation.navigateToPlayerPreferences
import com.hearyet.app.settings.navigation.navigateToSubtitlePreferences
import com.hearyet.app.settings.navigation.navigateToThumbnailPreferencesScreen
import com.hearyet.app.settings.navigation.playerPreferencesEntry
import com.hearyet.app.settings.navigation.settingsEntry
import com.hearyet.app.settings.navigation.subtitlePreferencesEntry
import com.hearyet.app.settings.navigation.thumbnailPreferencesEntry

fun EntryProviderScope<NavKey>.settingsNavGraph(
    backStack: NavBackStack<NavKey>,
) {
    settingsEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
        onItemClick = { setting ->
            when (setting) {
                Setting.MEDIA_LIBRARY -> backStack.navigateToMediaLibraryPreferencesScreen()
                Setting.PLAYER -> backStack.navigateToPlayerPreferences()
                Setting.GESTURES -> backStack.navigateToGesturePreferences()
                Setting.DECODER -> backStack.navigateToDecoderPreferences()
                Setting.AUDIO -> backStack.navigateToAudioPreferences()
                Setting.SUBTITLE -> backStack.navigateToSubtitlePreferences()
                Setting.GENERAL -> backStack.navigateToGeneralPreferences()
                Setting.ABOUT -> backStack.navigateToAboutPreferences()
            }
        },
    )
    mediaLibraryPreferencesEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
        onFolderSettingClick = backStack::navigateToFolderPreferencesScreen,
        onThumbnailSettingClick = backStack::navigateToThumbnailPreferencesScreen,
    )
    thumbnailPreferencesEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
    )
    folderPreferencesEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
    )
    playerPreferencesEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
    )
    gesturePreferencesEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
    )
    decoderPreferencesEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
    )
    audioPreferencesEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
    )
    subtitlePreferencesEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
    )
    generalPreferencesEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
    )
    aboutPreferencesEntry(
        onLibrariesClick = backStack::navigateToLibraries,
        onViewIntroAgain = { backStack.add(OnboardingRoute) },
        onNavigateUp = { backStack.removeLastIfNotRoot() },
    )
    librariesEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
    )
}
