package com.hearyet.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.hearyet.app.core.common.service.system.SystemService
import com.hearyet.app.core.data.repository.RecentActivityRepository
import com.hearyet.app.core.media.network.proxy.NetworkStreamingProxy
import com.hearyet.app.core.media.services.MediaOperationsService
import com.hearyet.app.core.model.RecentActivityEntry
import com.hearyet.app.core.ui.color.HearYetColors
import com.hearyet.app.core.ui.component.rememberMotionPreferences
import com.hearyet.app.core.ui.theme.HearYetTheme
import com.hearyet.app.navigation.HomeRoute
import com.hearyet.app.navigation.SplashRoute
import com.hearyet.app.navigation.hearYetNavGraph
import com.hearyet.app.navigation.mediaNavGraph
import com.hearyet.app.navigation.replaceRoot
import com.hearyet.app.navigation.settingsNavGraph
import com.hearyet.app.navigation.startupRoute
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var mediaOperationsService: MediaOperationsService

    @Inject
    lateinit var systemService: SystemService

    @Inject
    lateinit var networkStreamingProxy: NetworkStreamingProxy

    @Inject
    lateinit var recentActivityRepository: RecentActivityRepository

    private val viewModel: MainViewModel by viewModels()

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) networkStreamingProxy.release()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        systemService.initialize(this@MainActivity)
        mediaOperationsService.initialize(this@MainActivity)
        var uiState: MainActivityUiState by mutableStateOf(MainActivityUiState.Loading)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    uiState = state
                }
            }
        }

        installSplashScreen().setKeepOnScreenCondition {
            when (uiState) {
                MainActivityUiState.Loading -> true
                is MainActivityUiState.Success -> false
            }
        }

        setContent {
            HearYetTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = HearYetColors.Background,
                ) {
                    val reduceMotion = rememberMotionPreferences().reduceMotion

                    // FE §6 — single root nav graph: Splash → Onboarding (first launch) or Home.
                    val rootBackStack = rememberNavBackStack(SplashRoute)

                    LaunchedEffect(uiState) {
                        val state = uiState
                        if (state is MainActivityUiState.Success) {
                            rootBackStack.replaceRoot(
                                startupRoute(state.preferences.hasCompletedOnboarding),
                            )
                        }
                    }

                    val provider = entryProvider {
                        hearYetNavGraph(
                            reduceMotion = reduceMotion,
                            onOnboardingFinished = { rootBackStack.replaceRoot(HomeRoute) },
                            onRecentActivity = { entry: RecentActivityEntry ->
                                lifecycleScope.launch { recentActivityRepository.record(entry) }
                            },
                            backStack = rootBackStack,
                        )
                        mediaNavGraph(context = this@MainActivity, backStack = rootBackStack)
                        settingsNavGraph(backStack = rootBackStack)
                    }

                    val entries = rememberDecoratedNavEntries(
                        backStack = rootBackStack,
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        entryProvider = provider,
                    )

                    NavDisplay(
                        entries = entries,
                        onBack = { rootBackStack.removeLastOrNull() },
                    )
                }
            }
        }
    }
}
