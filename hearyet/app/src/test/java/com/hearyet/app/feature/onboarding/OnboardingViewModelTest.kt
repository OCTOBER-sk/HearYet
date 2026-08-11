package com.hearyet.app.feature.onboarding

import android.net.Uri
import androidx.core.net.toUri
import com.hearyet.app.core.data.repository.fake.FakePreferencesRepository
import com.hearyet.app.core.media.services.MediaFolder
import com.hearyet.app.core.media.services.MediaService
import com.hearyet.app.core.media.services.MediaVideo
import com.hearyet.app.core.media.sync.MediaSynchronizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OnboardingViewModelTest {

    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
    private val preferencesRepository = FakePreferencesRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        mediaService: MediaService = FakeMediaService(videos = emptyList()),
        mediaSynchronizer: MediaSynchronizer = FakeMediaSynchronizer(),
    ) = OnboardingViewModel(
        preferencesRepository = preferencesRepository,
        mediaService = mediaService,
        mediaSynchronizer = mediaSynchronizer,
    )

    @Test
    fun currentPage_startsAtZero() = runTest {
        val viewModel = createViewModel()

        assertEquals(0, viewModel.currentPage.value)
    }

    @Test
    fun onContinue_advancesPageUntilLast() = runTest {
        val viewModel = createViewModel()

        viewModel.onContinue()
        assertEquals(1, viewModel.currentPage.value)

        viewModel.onContinue()
        viewModel.onContinue()
        viewModel.onContinue()
        viewModel.onContinue()
        assertEquals(OnboardingViewModel.LAST_PAGE, viewModel.currentPage.value)
    }

    @Test
    fun onSkip_setsCompletedFlagAndEmitsFinished() = runTest {
        val viewModel = createViewModel()

        val finishedEvents = mutableListOf<Unit>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.finished.toList(finishedEvents)
        }
        viewModel.onSkip()
        advanceUntilIdle()
        collector.cancel()

        assertTrue(finishedEvents.isNotEmpty())
        assertTrue(preferencesRepository.applicationPreferences.value.hasCompletedOnboarding)
    }

    @Test
    fun onGetStarted_setsCompletedFlagAndEmitsFinished() = runTest {
        val viewModel = createViewModel()

        val finishedEvents = mutableListOf<Unit>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.finished.toList(finishedEvents)
        }
        viewModel.onGetStarted()
        advanceUntilIdle()
        collector.cancel()

        assertTrue(finishedEvents.isNotEmpty())
        assertTrue(preferencesRepository.applicationPreferences.value.hasCompletedOnboarding)
    }

    @Test
    fun onPageChanged_tracksSwipePosition() = runTest {
        val viewModel = createViewModel()

        viewModel.onPageChanged(3)

        assertEquals(3, viewModel.currentPage.value)
        assertFalse(preferencesRepository.applicationPreferences.value.hasCompletedOnboarding)
    }

    @Test
    fun onPermissionGranted_startsMediaSync() = runTest {
        val mediaSynchronizer = FakeMediaSynchronizer()
        val viewModel = createViewModel(mediaSynchronizer = mediaSynchronizer)

        viewModel.onPermissionGranted()
        advanceUntilIdle()

        assertTrue(mediaSynchronizer.syncStarted)
    }

    @Test
    fun onPermissionGranted_scansMediaAndCompletes() = runTest {
        val videos = (1..3).map { mediaVideo(id = it.toLong()) }
        val viewModel = createViewModel(mediaService = FakeMediaService(videos = videos))

        viewModel.onPermissionGranted()
        advanceUntilIdle()

        assertEquals(ScanState.Completed, viewModel.scanState.value)
        assertEquals(3, viewModel.scanProgress.value.scanned)
        assertEquals(3, viewModel.scanProgress.value.total)
    }

    @Test
    fun onPermissionGranted_scansEmptyLibraryAndCompletes() = runTest {
        val viewModel = createViewModel()

        viewModel.onPermissionGranted()
        advanceUntilIdle()

        assertEquals(ScanState.Completed, viewModel.scanState.value)
        assertEquals(0, viewModel.scanProgress.value.scanned)
        assertEquals(0, viewModel.scanProgress.value.total)
    }

    @Test
    fun onPermissionGranted_reportsProgressWhileScanning() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val videos = (1..120).map { mediaVideo(id = it.toLong()) }
        val viewModel = createViewModel(
            mediaService = FakeMediaService(videos = videos, pageDelayMillis = 1),
        )

        viewModel.onPermissionGranted()
        runCurrent()
        assertEquals(ScanState.Scanning, viewModel.scanState.value)
        assertEquals(0, viewModel.scanProgress.value.scanned)
        assertEquals(120, viewModel.scanProgress.value.total)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(50, viewModel.scanProgress.value.scanned)

        advanceUntilIdle()
        assertEquals(ScanState.Completed, viewModel.scanState.value)
        assertEquals(120, viewModel.scanProgress.value.scanned)
        assertEquals(120, viewModel.scanProgress.value.total)
        Dispatchers.resetMain()
    }
}

private class FakeMediaService(
    private val videos: List<MediaVideo>,
    private val pageDelayMillis: Long = 0,
) : MediaService {
    override fun observeFolders(folderPath: String?): Flow<List<MediaFolder>> = flowOf(emptyList())

    override fun observeVideos(folderPath: String?): Flow<List<MediaVideo>> = flowOf(videos)

    override suspend fun fetchFolders(folderPath: String?): List<MediaFolder> = emptyList()

    override suspend fun fetchVideos(folderPath: String?): List<MediaVideo> = videos

    override suspend fun countVideos(folderPath: String?): Int = videos.size

    override suspend fun fetchVideosAfter(afterId: Long, limit: Int, folderPath: String?): List<MediaVideo> {
        if (pageDelayMillis > 0) {
            delay(pageDelayMillis)
        }
        return videos.filter { it.id > afterId }.take(limit)
    }

    override suspend fun findVideo(uri: Uri): MediaVideo? = videos.firstOrNull { it.uri == uri }

    override suspend fun findFolder(path: String): MediaFolder? = null
}

private class FakeMediaSynchronizer : MediaSynchronizer {
    var syncStarted = false
        private set

    override suspend fun refresh(path: String?): Boolean = true

    override fun startSync() {
        syncStarted = true
    }

    override fun stopSync() = Unit
}

private fun mediaVideo(id: Long): MediaVideo = MediaVideo(
    id = id,
    uri = "content://media/external/video/media/$id".toUri(),
    path = "/storage/emulated/0/Movies/video$id.mp4",
    title = "video$id.mp4",
    parentPath = "/storage/emulated/0/Movies",
    displayName = "video$id.mp4",
    duration = 60_000,
    size = 1_024,
    width = 1920,
    height = 1080,
    dateModified = 1_700_000_000,
)
