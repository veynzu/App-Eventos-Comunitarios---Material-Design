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
import com.eventoscomunitarios.app.core.data.InMemoryContentAnalysisRepository
import com.eventoscomunitarios.app.domain.model.*
import com.eventoscomunitarios.app.domain.usecase.ContentAnalysisUseCases
import com.eventoscomunitarios.app.domain.repository.MediaRepository

/** Cada prueba recibe datos aislados: no modifica el estado de otra prueba ni el de la app. */
class TestDependencies {
    // Respuestas explícitas y deterministas SOLO para pruebas; producción siempre consulta Functions.
    val analysisRepository = InMemoryContentAnalysisRepository(
        textResult = TextAssessment(true, emptyList(), null, "fixture-text-review"),
        eventResult = EventAssessment(TextAssessment(true, emptyList(), null), ImageAssessment(null, ""),
            emptyList(), emptyList(), false, "fixture-event-review"))
    val analysis = ContentAnalysisUseCases(analysisRepository, object : MediaRepository {
        override suspend fun uploadImage(userId: String, localUri: String, folder: String): String = error("Solo fixture")
        override suspend fun uploadAnalysisImage(userId: String, uri: String) = "event-drafts/$userId/fixture"
        override suspend fun deleteImage(url: String) = Unit
    })
    val locationRepository = InMemoryLocationRepository(Coordinates.ARMENIA, "Armenia, Quindío")
    val locations = LocationUseCases(locationRepository)
    val eventRepository = InMemoryEventRepository()
    val userRepository = InMemoryUserRepository()
    val communityRepository = InMemoryCommunityRepository()
    val events = EventUseCases(eventRepository, userRepository, communityRepository)
    val users = UserUseCases(userRepository, eventRepository, communityRepository, events)
    val community = CommunityUseCases(communityRepository, eventRepository, userRepository)
}
