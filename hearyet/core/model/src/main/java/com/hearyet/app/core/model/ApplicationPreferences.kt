package com.hearyet.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ApplicationPreferences(
    val sortBy: Sort.By = Sort.By.TITLE,
    val sortOrder: Sort.Order = Sort.Order.ASCENDING,
    val hasCompletedOnboarding: Boolean = false,
    val markLastPlayedMedia: Boolean = true,
    val excludeFolders: List<String> = emptyList(),
    val mediaViewMode: MediaViewMode = MediaViewMode.FOLDERS,
    val mediaLayoutMode: MediaLayoutMode = MediaLayoutMode.LIST,

    // FE §9.5 — Guest display name, stored locally (DataStore) and reused to
    // pre-fill the name entry on future joins. Null = never entered, fall back
    // to the device model.
    val guestDisplayName: String? = null,

    // FE §9.7 — Guest greeting chime (BE §14). Default ON, DataStore-persisted
    // like every other setting.
    val greetingChimeEnabled: Boolean = true,

    // Home guide cards — dismissed ones are persisted so they never reappear.
    val dismissedGuideCards: List<GuideCardType> = emptyList(),

    // Fields
    val showDurationField: Boolean = true,
    val showFolderDurationField: Boolean = true,
    val showExtensionField: Boolean = false,
    val showPathField: Boolean = true,
    val showResolutionField: Boolean = false,
    val showSizeField: Boolean = false,
    val showThumbnailField: Boolean = true,
    val showPlayedProgress: Boolean = true,

    // Thumbnail generation
    val thumbnailGenerationStrategy: ThumbnailGenerationStrategy = ThumbnailGenerationStrategy.FRAME_AT_PERCENTAGE,
    val thumbnailFramePosition: Float = DEFAULT_THUMBNAIL_FRAME_POSITION,
) {

    companion object {
        const val DEFAULT_THUMBNAIL_FRAME_POSITION = 0.33f
    }
}
