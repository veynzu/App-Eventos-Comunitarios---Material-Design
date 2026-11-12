package com.eventoscomunitarios.app

import com.eventoscomunitarios.app.core.data.InMemoryCommunityRepository
import com.eventoscomunitarios.app.core.data.InMemoryEventRepository
import com.eventoscomunitarios.app.core.data.InMemoryUserRepository
import com.eventoscomunitarios.app.core.data.InMemoryContentAnalysisRepository
import com.eventoscomunitarios.app.domain.repository.ContentAnalysisRepository
import com.eventoscomunitarios.app.core.di.RepositoryModule
import com.eventoscomunitarios.app.domain.repository.CommunityRepository
import com.eventoscomunitarios.app.domain.repository.EventRepository
import com.eventoscomunitarios.app.domain.repository.MediaRepository
import com.eventoscomunitarios.app.domain.repository.UserRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/** Solo las pruebas UI usan datos de demostración; la app instalada usa Firebase. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [RepositoryModule::class])
object TestRepositoryModule {
    @Provides @Singleton fun contentAnalysis(): ContentAnalysisRepository = InMemoryContentAnalysisRepository()

    @Provides @Singleton fun events(): EventRepository = InMemoryEventRepository()
    @Provides @Singleton fun users(): UserRepository = InMemoryUserRepository()
    @Provides @Singleton fun community(): CommunityRepository = InMemoryCommunityRepository()
    @Provides @Singleton fun media(): MediaRepository = object : MediaRepository {
        override suspend fun uploadImage(userId: String, localUri: String, folder: String): String =
            error("Las pruebas UI no deben subir imágenes. Usa FirebaseIntegrationTest para Storage.")
        override suspend fun deleteImage(url: String) = Unit
    }
}
