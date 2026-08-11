package com.hearyet.app.navigation

import androidx.navigation3.runtime.NavKey
import com.hearyet.app.feature.videopicker.navigation.MediaPickerRoute
import com.hearyet.app.settings.navigation.SettingsRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class HearYetNavigationTest {

    @Test
    fun startupRoute_usesOnboardingUntilItIsCompleted() {
        assertEquals(OnboardingRoute, startupRoute(hasCompletedOnboarding = false))
        assertEquals(HomeRoute, startupRoute(hasCompletedOnboarding = true))
    }

    @Test
    fun phaseFourRoutes_areAllNavigationKeys() {
        val routes: List<NavKey> = listOf(
            OnboardingRoute,
            HomeRoute,
            JoinRoute,
            InSessionGuestRoute,
            InSessionHostRoute,
            SessionEndedRoute,
            PermissionRequiredRoute(),
        )

        assertEquals(7, routes.size)
    }

    @Test
    fun replaceRoot_discardsOldRootAndNestedEntries() {
        val backStack = androidx.navigation3.runtime.NavBackStack<NavKey>(
            MediaPickerRoute(),
            SettingsRoute,
        )

        backStack.replaceRoot(HomeRoute)

        assertEquals(1, backStack.size)
        assertEquals(HomeRoute, backStack.first())
    }
}
