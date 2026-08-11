package com.hearyet.app.core.domain

import com.hearyet.app.core.common.Dispatcher
import com.hearyet.app.core.common.NextDispatchers
import com.hearyet.app.core.data.repository.VaultRepository
import com.hearyet.app.core.model.Sort
import com.hearyet.app.core.model.Video
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Returns videos currently hidden in the vault, sorted by the given [Sort] criteria.
 */
class GetHiddenVideosUseCase @Inject constructor(
    private val vaultRepository: VaultRepository,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher,
) {

    operator fun invoke(sort: Sort): Flow<List<Video>> {
        return vaultRepository.observeHiddenVideos()
            .map { videos -> videos.sortedWith(sort.videoComparator()) }
            .flowOn(defaultDispatcher)
    }
}
