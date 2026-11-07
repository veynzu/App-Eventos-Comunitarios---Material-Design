package com.eventoscomunitarios.app

import com.eventoscomunitarios.app.core.data.*
import com.eventoscomunitarios.app.domain.model.*
import com.eventoscomunitarios.app.domain.repository.LocationRepository
import com.eventoscomunitarios.app.domain.repository.CommunityRepository
import com.eventoscomunitarios.app.domain.repository.MediaRepository
import com.eventoscomunitarios.app.domain.usecase.*
import com.eventoscomunitarios.app.features.home.HomeUiState
import com.eventoscomunitarios.app.features.map.MapUiState
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class LocationAndRecurrenceTest {
    private val eventsRepository = InMemoryEventRepository()
    private val usersRepository = InMemoryUserRepository()
    private val socialRepository = InMemoryCommunityRepository()
    private val events = EventUseCases(eventsRepository, usersRepository, socialRepository)
    private val community = CommunityUseCases(socialRepository, eventsRepository, usersRepository)
    private val owner = "organizer-carlos"
    private val participant = "participant-ana"
    private val moderator = "administrator-laura"

    private fun draft() = events.events.value.first().copy(id = 0, startsAt = System.currentTimeMillis() + 86400000,
        endsAt = System.currentTimeMillis() + 90000000, locationConfirmed = true)

    private suspend fun pastEvent(): Event {
        val original = events.findEvent(1)!!
        eventsRepository.save(original.copy(startsAt = System.currentTimeMillis() - 7200000,
            endsAt = System.currentTimeMillis() - 1000, isFinished = true))
        return events.findEvent(1)!!
    }

    @Test fun coordinatesValidateRangesAndComputeDistanceWithoutStoredLabels() {
        assertTrue(Coordinates(0.0, 0.0).isValid)
        assertFalse(Coordinates(Double.NaN, 0.0).isValid)
        assertFalse(Coordinates(91.0, 0.0).isValid)
        assertFalse(Coordinates(0.0, -181.0).isValid)
        assertEquals(111.195, Coordinates(0.0, 0.0).distanceKmTo(Coordinates(0.0, 1.0)), 0.01)
        assertEquals(0.0, Coordinates.ARMENIA.distanceKmTo(Coordinates.ARMENIA), 0.001)
    }

    @Test fun geographicFiltersUseSelectedCoordinatesAndHideUnconfirmedLocations() {
        val event = events.findEvent(1)!!.copy(latitude = 0.0, longitude = 0.0, distanceKm = 0.0, locationConfirmed = true)
        val far = SearchLocation(Coordinates(0.0, 1.0), "Otra zona", SearchLocationSource.SELECTED)
        assertTrue(HomeUiState(events = listOf(event), searchLocation = far, searchRadiusKm = 5f).filteredEvents.isEmpty())
        assertTrue(MapUiState(events = listOf(event), searchLocation = far).visibleEvents.isEmpty())
        val near = far.copy(coordinates = Coordinates(0.0, 0.001))
        assertEquals(event.id, HomeUiState(events = listOf(event), searchLocation = near).filteredEvents.single().id)
        assertTrue(HomeUiState(events = listOf(event.copy(locationConfirmed = false)), searchLocation = near).filteredEvents.isEmpty())
    }

    @Test fun deviceLocationAndPermissionFallbackSelectCorrectSearchOrigin() = runBlocking {
        val repository = InMemoryLocationRepository()
        val locations = LocationUseCases(repository)
        locations.initialize()
        assertEquals(SearchLocationSource.FALLBACK, locations.search.value.source)
        assertEquals(Coordinates.ARMENIA, locations.search.value.coordinates)
        repository.point = Coordinates(4.7, -74.1)
        locations.useDeviceLocation()
        assertEquals(repository.point, locations.search.value.coordinates)
        assertEquals(SearchLocationSource.DEVICE, locations.search.value.source)
    }

    @Test fun outdoorFilterUsesExplicitEventDataRatherThanTheAddressText() {
        val event = events.findEvent(1)!!.copy(location = "Calle 20", isOutdoor = true)
        val state = MapUiState(events = listOf(event), activeFilters = setOf(com.eventoscomunitarios.app.features.map.MapFilter.OUTDOOR))
        assertEquals(event.id, state.visibleEvents.single().id)
        assertTrue(state.copy(events = listOf(event.copy(location = "Parque comercial", isOutdoor = false))).visibleEvents.isEmpty())
    }

    @Test fun aLateGpsResponseDoesNotReplaceManuallyChosenSearchPoint() = runBlocking {
        val response = CompletableDeferred<Coordinates?>()
        val repository = object : LocationRepository {
            override suspend fun currentLocation() = response.await()
            override suspend fun addressFor(coordinates: Coordinates): String? = null
        }
        val locations = LocationUseCases(repository)
        val request = launch { locations.initialize() }
        yield()
        val selected = Coordinates(6.2, -75.6)
        locations.select(selected, "Zona elegida")
        response.complete(Coordinates.ARMENIA)
        request.join()
        assertEquals(selected, locations.search.value.coordinates)
        assertEquals(SearchLocationSource.SELECTED, locations.search.value.source)
    }

    @Test fun creatingAndEditingRequireAConfirmedMapPoint() = runBlocking {
        assertTrue(events.create(owner, draft().copy(locationConfirmed = false)).isFailure)
        assertTrue(events.create(owner, draft().copy(latitude = 95.0)).isFailure)
        val event = events.findEvent(1)!!
        assertTrue(events.update(owner, event.copy(locationConfirmed = false)).isFailure)
        events.update(owner, event.copy(latitude = 6.2, longitude = -75.6, location = "Sitio confirmado")).getOrThrow()
        assertEquals(6.2, events.findEvent(1)!!.latitude, 0.0001)
        assertEquals(EventModerationStatus.PENDING, events.findEvent(1)!!.moderationStatus)
    }

    @Test fun reviewsRequireFinishedEventAndConfirmedParticipation() = runBlocking {
        assertFalse(community.canComment(participant, 1))
        assertTrue(community.addComment(participant, 1, "Aún no ocurrió").isFailure)
        pastEvent()
        assertTrue(community.canComment(participant, 1))
        assertTrue(community.addComment(participant, 1, "Buena experiencia").isSuccess)
        assertTrue(community.addComment(owner, 1, "No estuve inscrito").isFailure)
        eventsRepository.saveRegistration(EventRegistration("participant-laura", 1, EventRegistrationStatus.WAITLIST, 1, 99))
        assertTrue(community.addComment("participant-laura", 1, "Solo lista de espera").isFailure)
        eventsRepository.deleteRegistration(participant, 1)
        assertTrue(community.addComment(participant, 1, "Inscripción cancelada").isFailure)
    }

    @Test fun relaunchKeepsFavoritesAndHistoryButRequiresNewApprovalAndEnrollment() = runBlocking {
        community.toggleFavorite(participant, 1).getOrThrow()
        community.toggleFollow(participant, owner).getOrThrow()
        val old = pastEvent()
        community.addComment(participant, old.id, "Reseña de la primera edición").getOrThrow()
        val next = events.relaunch(owner, old.id, draft()).getOrThrow()
        assertNotEquals(old.id, next.id)
        assertEquals(old.seriesId, next.seriesId)
        assertEquals(old.id, next.previousEventId)
        assertEquals(EventModerationStatus.PENDING, next.moderationStatus)
        assertFalse(next.isFinished)
        assertEquals(0, next.attendeeCount)
        assertFalse(events.registrations.value.any { it.eventId == next.id })
        assertTrue(community.comments.value.all { it.eventId == old.id })
        assertFalse(community.notifications.value.any { it.userId == participant && it.eventId == next.id })
        events.moderate(moderator, next.id, EventModerationStatus.APPROVED).getOrThrow()
        assertEquals(1, community.notifications.value.count { it.userId == participant && it.eventId == next.id && it.title == "Nueva edición de tu evento favorito" })
        assertEquals(1, community.notifications.value.count { it.userId == participant && it.eventId == next.id })
        assertFalse(community.canComment(participant, next.id))
        events.register(participant, next.id).getOrThrow()
        assertEquals(1, events.registrations.value.count { it.eventId == next.id && it.userId == participant })
        community.toggleFavorite(participant, next.id).getOrThrow()
        assertTrue(community.favorites.value.isEmpty())
    }

    @Test fun relaunchRejectsOtherAuthorsUnfinishedEventsAndInvalidDates() = runBlocking {
        assertTrue(events.relaunch(owner, 1, draft()).isFailure)
        val old = pastEvent()
        assertTrue(events.relaunch(participant, old.id, draft()).isFailure)
        assertTrue(events.relaunch(moderator, old.id, draft()).isFailure)
        assertTrue(events.relaunch(owner, old.id, draft().copy(startsAt = 1, endsAt = 2)).isFailure)
    }

    @Test fun rejectionDoesNotNotifyFavoritesAndRemovingOldEditionKeepsTheirSubscription() = runBlocking {
        community.toggleFavorite(participant, 1).getOrThrow()
        val old = pastEvent()
        val next = events.relaunch(owner, old.id, draft()).getOrThrow()
        events.moderate(moderator, next.id, EventModerationStatus.REJECTED, "Revisa el lugar").getOrThrow()
        assertFalse(community.notifications.value.any { it.userId == participant && it.eventId == next.id })
        events.delete(owner, old.id).getOrThrow()
        assertEquals(old.seriesId, community.favorites.value.single().eventId)
        events.delete(owner, next.id).getOrThrow()
        assertTrue(community.favorites.value.isEmpty())
    }

    @Test fun aDifferentSessionStartsAtTheDeviceInsteadOfThePreviousUsersChosenArea() = runBlocking {
        val repository = InMemoryLocationRepository(Coordinates.ARMENIA)
        val locations = LocationUseCases(repository)
        locations.initialize("first")
        locations.select(Coordinates(6.2, -75.6), "Zona del primer usuario", "first")
        locations.initialize("second")
        assertEquals(Coordinates.ARMENIA, locations.search.value.coordinates)
        assertEquals(SearchLocationSource.DEVICE, locations.search.value.source)
    }

    @Test fun failedFavoriteLookupLeavesApprovalPendingForARetry() = runBlocking {
        val unavailable = object : CommunityRepository by socialRepository {
            override suspend fun favoriteUserIds(seriesId: Int): Set<String> = error("Sin conexión")
        }
        val cases = EventUseCases(eventsRepository, usersRepository, unavailable)
        val event = cases.create(owner, draft()).getOrThrow()
        assertTrue(cases.moderate(moderator, event.id, EventModerationStatus.APPROVED).isFailure)
        assertEquals(EventModerationStatus.PENDING, cases.findEvent(event.id)!!.moderationStatus)
    }

    @Test fun deletingAnOldEditionDoesNotDeleteTheImageSharedByTheNextEdition() = runBlocking {
        val deleted = mutableListOf<String>()
        val media = object : MediaRepository {
            override suspend fun uploadImage(userId: String, localUri: String, folder: String): String = error("No subir imágenes en esta prueba")
            override suspend fun deleteImage(url: String) { deleted += url }
        }
        val cases = EventUseCases(eventsRepository, usersRepository, socialRepository, media)
        val photo = "https://firebasestorage.googleapis.com/v0/b/demo/o/photo?alt=media&token=local"
        val old = pastEvent().copy(photoUri = photo)
        eventsRepository.save(old)
        val next = cases.relaunch(owner, old.id, draft().copy(photoUri = photo)).getOrThrow()
        cases.delete(owner, old.id).getOrThrow()
        assertTrue(deleted.isEmpty())
        assertEquals(photo, cases.findEvent(next.id)!!.photoUri)
        cases.delete(owner, next.id).getOrThrow()
        assertEquals(listOf(photo), deleted)
    }
}
