package com.hearyet.app.core.media.services

import android.net.Uri
import kotlinx.coroutines.flow.Flow

/**
 * Service for accessing media (videos and folders) from the device's storage.
 *
 * This service provides both reactive (Flow-based) and one-shot methods for
 * fetching media. The reactive methods automatically emit new values when
 * the underlying media changes (e.g., files added/removed).
 */
interface MediaService {

    /**
     * Observes all unique folders containing videos under the given path.
     * Emits a new list whenever the underlying media changes.
     *
     * @param folderPath The root path to search for videos, or null to scan all storage volumes.
     * @return A flow of folder lists. Each folder represents a directory containing at least one video.
     *         Folder statistics (videosCount, foldersCount) are set to 0 - compute at use case layer.
     */
    fun observeFolders(folderPath: String? = null): Flow<List<MediaFolder>>

    /**
     * Observes all videos under the given path recursively.
     * Emits a new list whenever the underlying media changes.
     *
     * @param folderPath The root path to search for videos, or null to scan all storage volumes.
     * @return A flow of video lists containing all videos found under the path.
     */
    fun observeVideos(folderPath: String? = null): Flow<List<MediaVideo>>

    /**
     * Fetches all unique folders containing videos under the given path (one-shot).
     *
     * @param folderPath The root path to search for videos, or null to scan all storage volumes.
     * @return List of folders, each representing a directory containing at least one video.
     *         Folder statistics (videosCount, foldersCount) are set to 0 - compute at use case layer.
     */
    suspend fun fetchFolders(folderPath: String? = null): List<MediaFolder>

    /**
     * Fetches all videos under the given path recursively (one-shot).
     *
     * @param folderPath The root path to search for videos, or null to scan all storage volumes.
     * @return List of all videos found under the path.
     */
    suspend fun fetchVideos(folderPath: String? = null): List<MediaVideo>

    /**
     * Counts all videos under the given path (one-shot).
     *
     * @param folderPath The root path to search for videos, or null to scan all storage volumes.
     * @return The number of videos found under the path.
     */
    suspend fun countVideos(folderPath: String? = null): Int

    /**
     * Fetches a page of videos whose id is greater than [afterId] (one-shot).
     *
     * Useful for paging through the library without a LIMIT clause, which some OEM
     * MediaStore providers reject.
     *
     * @param afterId Only videos with an id larger than this are returned.
     * @param limit The maximum number of videos to return.
     * @param folderPath The root path to search for videos, or null to scan all storage volumes.
     * @return Up to [limit] videos found under the path, ordered by id ascending.
     */
    suspend fun fetchVideosAfter(afterId: Long, limit: Int, folderPath: String? = null): List<MediaVideo>

    /**
     * Finds a specific video by its content URI.
     *
     * @param uri The content URI of the video.
     * @return The video if found, null otherwise.
     */
    suspend fun findVideo(uri: Uri): MediaVideo?

    /**
     * Finds a specific folder by its path.
     *
     * @param path The absolute path to the folder.
     * @return The folder if it exists and contains videos, null otherwise.
     */
    suspend fun findFolder(path: String): MediaFolder?
}
