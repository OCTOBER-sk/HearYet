package com.hearyet.app.core.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.hearyet.app.core.data.repository.LocalMediaRepository
import com.hearyet.app.core.data.repository.LocalNetworkConnectionRepository
import com.hearyet.app.core.data.repository.LocalPreferencesRepository
import com.hearyet.app.core.data.repository.LocalRecentActivityRepository
import com.hearyet.app.core.data.repository.LocalSearchHistoryRepository
import com.hearyet.app.core.data.repository.LocalVaultPinRepository
import com.hearyet.app.core.data.repository.LocalVaultRepository
import com.hearyet.app.core.data.repository.MediaRepository
import com.hearyet.app.core.data.repository.NetworkConnectionRepository
import com.hearyet.app.core.data.repository.PreferencesRepository
import com.hearyet.app.core.data.repository.RecentActivityRepository
import com.hearyet.app.core.data.repository.SearchHistoryRepository
import com.hearyet.app.core.data.repository.VaultPinRepository
import com.hearyet.app.core.data.repository.VaultRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {

    @Binds
    fun bindsMediaRepository(
        videoRepository: LocalMediaRepository,
    ): MediaRepository

    @Binds
    @Singleton
    fun bindsPreferencesRepository(
        preferencesRepository: LocalPreferencesRepository,
    ): PreferencesRepository

    @Binds
    @Singleton
    fun bindsSearchHistoryRepository(
        searchHistoryRepository: LocalSearchHistoryRepository,
    ): SearchHistoryRepository

    @Binds
    @Singleton
    fun bindsVaultRepository(
        vaultRepository: LocalVaultRepository,
    ): VaultRepository

    @Binds
    @Singleton
    fun bindsVaultPinRepository(
        vaultPinRepository: LocalVaultPinRepository,
    ): VaultPinRepository

    @Binds
    @Singleton
    fun bindsNetworkConnectionRepository(
        networkConnectionRepository: LocalNetworkConnectionRepository,
    ): NetworkConnectionRepository

    @Binds
    @Singleton
    fun bindsRecentActivityRepository(
        recentActivityRepository: LocalRecentActivityRepository,
    ): RecentActivityRepository
}
