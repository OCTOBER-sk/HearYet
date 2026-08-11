package com.hearyet.app.core.data.mappers

import com.hearyet.app.core.common.Utils
import com.hearyet.app.core.database.entities.MediumStateEntity
import com.hearyet.app.core.media.services.MediaVideo
import com.hearyet.app.core.model.Video
import java.util.Date

internal fun MediaVideo.toVideo(mediaState: MediumStateEntity? = null) = Video(
    id = id,
    uriString = uri.toString(),
    duration = duration,
    height = height,
    width = width,
    path = path,
    size = size,
    nameWithExtension = title,
    parentPath = parentPath,
    dateModified = dateModified,
    formattedDuration = Utils.formatDurationMillis(duration),
    formattedFileSize = Utils.formatFileSize(size),
    playbackPosition = mediaState?.playbackPosition,
    lastPlayedAt = mediaState?.lastPlayedTime?.let { Date(it) },
)
