package com.eventoscomunitarios.app

import com.eventoscomunitarios.app.core.data.InMemoryCommunityRepository
import com.eventoscomunitarios.app.core.data.InMemoryEventRepository
import com.eventoscomunitarios.app.core.data.InMemoryUserRepository
import com.eventoscomunitarios.app.domain.usecase.CommunityUseCases
import com.eventoscomunitarios.app.domain.usecase.EventUseCases
import com.eventoscomunitarios.app.domain.usecase.UserUseCases
import com.eventoscomunitarios.app.domain.usecase.LocationUseCases
import com.eventoscomunitarios.app.core.data.InMemoryLocationRepository
import com.eventoscomunitarios.app.domain.model.Coordinates

/** Cada prueba recibe datos aislados: no modifica el estado de otra prueba ni el de la app. */
class TestDependencies {
    val locationRepository = InMemoryLocationRepository(Coordinates.ARMENIA, "Armenia, Quindío")
    val locations = LocationUseCases(locationRepository)
    val eventRepository = InMemoryEventRepository()
    val userRepository = InMemoryUserRepository()
    val communityRepository = InMemoryCommunityRepository()
    val events = EventUseCases(eventRepository, userRepository, communityRepository)
    val users = UserUseCases(userRepository, eventRepository, communityRepository, events)
    val community = CommunityUseCases(communityRepository, eventRepository, userRepository)
}
