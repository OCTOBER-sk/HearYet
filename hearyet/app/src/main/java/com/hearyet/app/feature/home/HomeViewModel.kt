package com.hearyet.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearyet.app.core.data.repository.RecentActivityRepository
import com.hearyet.app.core.model.RecentActivityEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HomeViewModel @Inject constructor(
    recentActivityRepository: RecentActivityRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = recentActivityRepository.observeRecentActivity()
        .map { entries -> HomeUiState(entries = entries, isLoading = false) }
        .onStart { emit(HomeUiState(entries = emptyList(), isLoading = true)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState(entries = emptyList(), isLoading = true),
        )
}

data class HomeUiState(
    val entries: List<RecentActivityEntry> = emptyList(),
    val isLoading: Boolean = false,
)
