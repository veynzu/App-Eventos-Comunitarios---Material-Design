package com.eventoscomunitarios.app

import com.eventoscomunitarios.app.core.data.InMemoryLocationRepository
import com.eventoscomunitarios.app.core.di.LocationModule
import com.eventoscomunitarios.app.domain.model.Coordinates
import com.eventoscomunitarios.app.domain.repository.LocationRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [LocationModule::class])
object TestLocationModule {
    @Provides @Singleton fun location(): LocationRepository = InMemoryLocationRepository(Coordinates.ARMENIA, "Armenia, Quindío")
}
