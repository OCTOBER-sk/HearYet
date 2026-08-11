package com.hearyet.app.core.data.mappers

import com.hearyet.app.core.media.services.MediaFolder
import com.hearyet.app.core.model.Folder

internal fun MediaFolder.toFolder() = Folder(
    name = name,
    path = path,
    dateModified = dateModified,
    totalSize = totalSize,
    totalDuration = totalDuration,
    videosCount = videosCount,
    foldersCount = foldersCount,
)
