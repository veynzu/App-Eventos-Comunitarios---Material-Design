package com.eventoscomunitarios.app.core.di

import com.eventoscomunitarios.app.core.data.FirebaseCommunityRepository
import com.eventoscomunitarios.app.core.data.FirebaseEventRepository
import com.eventoscomunitarios.app.core.data.FirebaseUserRepository
import com.eventoscomunitarios.app.core.data.FirebaseMediaRepository
import com.eventoscomunitarios.app.core.data.FirebaseContentAnalysisRepository
import com.eventoscomunitarios.app.domain.repository.ContentAnalysisRepository
import com.eventoscomunitarios.app.domain.repository.CommunityRepository
import com.eventoscomunitarios.app.domain.repository.EventRepository
import com.eventoscomunitarios.app.domain.repository.UserRepository
import com.eventoscomunitarios.app.domain.repository.MediaRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** La UI conserva los contratos de clase; Hilt conecta las implementaciones de Firebase. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindContentAnalysisRepository(implementation: FirebaseContentAnalysisRepository): ContentAnalysisRepository

    @Binds @Singleton
    abstract fun bindEventRepository(implementation: FirebaseEventRepository): EventRepository

    @Binds @Singleton
    abstract fun bindUserRepository(implementation: FirebaseUserRepository): UserRepository

    @Binds @Singleton
    abstract fun bindCommunityRepository(implementation: FirebaseCommunityRepository): CommunityRepository

    @Binds @Singleton
    abstract fun bindMediaRepository(implementation: FirebaseMediaRepository): MediaRepository
}
