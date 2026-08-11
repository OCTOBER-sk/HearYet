package com.hearyet.app.feature.videopicker.screens

import com.hearyet.app.core.model.Folder

sealed interface MediaState {
    data object Loading : MediaState
    data class Success(val data: Folder?) : MediaState
}
