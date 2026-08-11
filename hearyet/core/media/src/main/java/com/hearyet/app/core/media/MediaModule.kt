package com.hearyet.app.core.media

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.hearyet.app.core.media.services.LocalMediaOperationsService
import com.hearyet.app.core.media.services.MediaOperationsService
import com.hearyet.app.core.media.services.MediaService
import com.hearyet.app.core.media.services.MediaStoreMediaService
import com.hearyet.app.core.media.sync.LocalMediaSynchronizer
import com.hearyet.app.core.media.sync.MediaSynchronizer
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface MediaModule {

    @Binds
    @Singleton
    fun bindsMediaSynchronizer(
        mediaSynchronizer: LocalMediaSynchronizer,
    ): MediaSynchronizer

    @Binds
    @Singleton
    fun bindMediaOperationsService(
        mediaService: LocalMediaOperationsService,
    ): MediaOperationsService

    @Binds
    @Singleton
    fun bindMediaService(
        mediaService: MediaStoreMediaService,
    ): MediaService
}
