package com.hearyet.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearyet.app.core.data.repository.PreferencesRepository
import com.hearyet.app.core.media.services.MediaService
import com.hearyet.app.core.media.sync.MediaSynchronizer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ScanState {
    Idle,
    Scanning,
    Completed,
}

data class ScanProgress(
    val scanned: Int = 0,
    val total: Int = 0,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val mediaService: MediaService,
    private val mediaSynchronizer: MediaSynchronizer,
) : ViewModel() {

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _scanState = MutableStateFlow(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    private val _finished = MutableSharedFlow<Unit>(replay = 1)
    val finished: SharedFlow<Unit> = _finished.asSharedFlow()

    private var scanJob: Job? = null

    fun onPageChanged(page: Int) {
        _currentPage.value = page
    }

    fun onContinue() {
        if (_currentPage.value < LAST_PAGE) {
            _currentPage.value += 1
        }
    }

    fun onSkip() {
        completeOnboarding()
    }

    fun onGetStarted() {
        completeOnboarding()
    }

    fun onPermissionGranted() {
        startScan()
    }

    private fun startScan() {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            mediaSynchronizer.startSync()
            val total = mediaService.countVideos()
            _scanProgress.value = ScanProgress(scanned = 0, total = total)
            _scanState.value = ScanState.Scanning
            var scanned = 0
            var afterId = 0L
            while (scanned < total) {
                val page = mediaService.fetchVideosAfter(afterId = afterId, limit = SCAN_PAGE_SIZE)
                if (page.isEmpty()) break
                scanned += page.size
                afterId = page.last().id
                _scanProgress.value = ScanProgress(scanned = scanned.coerceAtMost(total), total = total)
            }
            _scanProgress.value = ScanProgress(scanned = total, total = total)
            _scanState.value = ScanState.Completed
        }
    }

    private fun completeOnboarding() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences { preferences ->
                preferences.copy(hasCompletedOnboarding = true)
            }
            _finished.emit(Unit)
        }
    }

    companion object {
        const val PAGE_COUNT = 3
        const val LAST_PAGE = PAGE_COUNT - 1
        private const val SCAN_PAGE_SIZE = 50
    }
}
