package com.eventoscomunitarios.app.core.di

import com.eventoscomunitarios.app.core.data.AndroidLocationRepository
import com.eventoscomunitarios.app.domain.repository.LocationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {
    @Binds @Singleton
    abstract fun bindLocationRepository(implementation: AndroidLocationRepository): LocationRepository
}
