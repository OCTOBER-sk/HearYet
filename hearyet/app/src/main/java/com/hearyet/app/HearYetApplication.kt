package com.hearyet.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import com.hearyet.app.core.common.di.ApplicationScope
import com.hearyet.app.core.data.repository.PreferencesRepository
import com.hearyet.app.crash.CrashActivity
import com.hearyet.app.crash.GlobalExceptionHandler
import com.hearyet.app.core.model.ApplicationPreferences
import com.hearyet.app.core.model.SessionHandle
import com.hearyet.app.core.model.SessionHolder
import com.hearyet.app.core.model.SessionStartProvider
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

@HiltAndroidApp
class HearYetApplication : Application(), SingletonImageLoader.Factory, SessionStartProvider {

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    /** BE §14.7 — live DataStore-backed application preferences for cross-module reads. */
    val applicationPreferences: StateFlow<ApplicationPreferences>
        get() = preferencesRepository.applicationPreferences

    /**
     * C11 — Set the active session handle so PlayerActivity can observe it.
     * Delegates to [SessionHolder] so `:feature:player` can read it without
     * depending on `:app`.
     */
    fun setActiveSession(handle: SessionHandle) {
        SessionHolder.active = handle
    }

    fun clearActiveSession() {
        SessionHolder.active = null
    }

    /** BE §2.1 — create a new Host session from the player controls. */
    fun createSession(displayName: String): SessionHandle {
        val coordinator = com.hearyet.app.sync.SessionCoordinator(this)
        coordinator.startAsHost(displayName)
        return coordinator
    }

    /** BE §2.1 — the in-player session button entry point (feature:player -> :app). */
    override fun startHostSession(displayName: String) {
        createSession(displayName)
    }

    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(applicationContext, CrashActivity::class.java))
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader
}
