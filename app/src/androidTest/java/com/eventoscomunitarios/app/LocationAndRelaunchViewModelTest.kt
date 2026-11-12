package com.eventoscomunitarios.app

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eventoscomunitarios.app.core.util.*
import com.eventoscomunitarios.app.domain.model.*
import com.eventoscomunitarios.app.domain.repository.LocationRepository
import com.eventoscomunitarios.app.features.create_event.CreateEventViewModel
import com.eventoscomunitarios.app.features.home.*
import com.eventoscomunitarios.app.features.map.*
import com.eventoscomunitarios.app.features.locationpicker.LocationPickerViewModel
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocationAndRelaunchViewModelTest {
    private val data = TestDependencies()
    private val models = ViewModelStore()
    private var sequence = 0
    @After fun close() = InstrumentationRegistry.getInstrumentation().runOnMainSync { models.clear() }
    private fun <T : ViewModel> track(model: T): T = model.also { models.put("model-${sequence++}", it) }
    private suspend fun await(predicate: () -> Boolean) = withTimeout(5000) { while (!predicate()) delay(10) }

    @Test fun centeringOnDeviceDoesNotSilentlyConfirmAnEventLocation() = runBlocking {
        val picker = track(LocationPickerViewModel(data.locationRepository))
        picker.start(null, "")
        await { !picker.uiState.value.isLocating }
        assertEquals(Coordinates.ARMENIA, picker.uiState.value.center)
        assertNull(picker.uiState.value.point)
        assertFalse(picker.uiState.value.canConfirm)
        picker.selectPoint(Coordinates(4.6, -75.7))
        await { !picker.uiState.value.isResolvingAddress }
        assertTrue(picker.uiState.value.canConfirm)
        assertEquals(Coordinates(4.6, -75.7), picker.uiState.value.point)
    }

    @Test fun missingGeocodingAllowsManualReferenceWithoutLosingCoordinates() = runBlocking {
        data.locationRepository.address = null
        val picker = track(LocationPickerViewModel(data.locationRepository))
        picker.start(null, "")
        picker.selectPoint(Coordinates.ARMENIA)
        await { !picker.uiState.value.isResolvingAddress }
        assertFalse(picker.uiState.value.canConfirm)
        picker.onAddressChange("Entrada principal del parque")
        assertTrue(picker.uiState.value.canConfirm)
        assertEquals(Coordinates.ARMENIA, picker.uiState.value.point)
    }

    @Test fun lateGeocodingDoesNotReplaceTheNewestPointOrManualReference() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val oldResponse = CompletableDeferred<String?>()
        val old = Coordinates.ARMENIA
        val latest = Coordinates(6.2, -75.6)
        val repository = object : LocationRepository {
            override suspend fun currentLocation(): Coordinates? = null
            override suspend fun addressFor(coordinates: Coordinates): String? = if (coordinates == old) {
                started.complete(Unit); oldResponse.await()
            } else "Dirección nueva"
        }
        val picker = track(LocationPickerViewModel(repository))
        picker.start(null, "")
        picker.selectPoint(old)
        started.await()
        picker.selectPoint(latest)
        await { !picker.uiState.value.isResolvingAddress }
        picker.onAddressChange("Referencia propia")
        oldResponse.complete("Dirección anterior")
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertEquals(latest, picker.uiState.value.point)
        assertEquals("Referencia propia", picker.uiState.value.address)
    }

    @Test fun searchPointIsSharedAndChangingItPreservesEachScreensFilters() = runBlocking {
        val home = track(HomeViewModel(data.events, data.users, data.community, data.locations))
        home.loadUser("participant-ana")
        await { home.uiState.value.loadResult == RequestResult.Success && !data.locations.search.value.isLocating }
        home.onSearchRadiusChange(3f); home.onFilterChange(HomeFilter.FREE); home.onCategoryChange(EventCategory.SPORTS)
        val point = Coordinates(4.7, -74.1)
        home.selectSearchLocation(point, "Zona elegida")
        await { home.uiState.value.searchLocation.coordinates == point }
        val map = track(MapViewModel(SavedStateHandle(mapOf("userId" to "participant-ana")), data.events, data.users, data.locations))
        await { map.uiState.value.searchLocation.coordinates == point }
        assertTrue(home.uiState.value.filteredEvents.isEmpty())
        map.onRadiusClick(); map.onFilterClick(MapFilter.TODAY)
        map.selectSearchLocation(Coordinates.ARMENIA, "Armenia seleccionada")
        await { home.uiState.value.searchLocation.coordinates == Coordinates.ARMENIA }
        assertEquals(3f, home.uiState.value.searchRadiusKm)
        assertEquals(HomeFilter.FREE, home.uiState.value.selectedFilter)
        assertEquals(EventCategory.SPORTS, home.uiState.value.selectedCategory)
        assertEquals(10, map.uiState.value.radiusKm)
        assertTrue(MapFilter.TODAY in map.uiState.value.activeFilters)
    }

    @Test fun relaunchFormCopiesThePlaceAndScheduleButRequiresNewDates() = runBlocking {
        val old = data.events.findEvent(1)!!.copy(startsAt = System.currentTimeMillis() - 7200000, endsAt = System.currentTimeMillis() - 1000, isFinished = true)
        data.eventRepository.save(old)
        val form = track(CreateEventViewModel(SavedStateHandle(mapOf("userId" to "organizer-carlos", "sourceEventId" to old.id)), data.events, data.users, data.analysis))
        await { form.uiState.value.sourceReady }
        assertEquals(old.title, form.uiState.value.title)
        assertEquals(old.coordinates, form.uiState.value.coordinates)
        assertEquals(eventFormTime(old.startsAt), form.uiState.value.time)
        assertEquals("", form.uiState.value.date)
        val count = data.events.events.value.size
        form.publishEvent()
        assertEquals(count, data.events.events.value.size)
        assertNotNull(form.uiState.value.dateError)
        val nextStart = System.currentTimeMillis() + 86400000
        form.onDateChange(eventFormDate(nextStart)); form.onTimeChange(eventFormTime(nextStart))
        form.onEndDateChange(eventFormDate(nextStart + 7200000)); form.onEndTimeChange(eventFormTime(nextStart + 7200000))
        form.publishEvent()
        await { form.uiState.value.publishResult != RequestResult.Loading }
        assertEquals(RequestResult.Success, form.uiState.value.publishResult)
        val next = data.events.events.value.last()
        assertEquals(old.seriesId, next.seriesId)
        assertEquals(old.id, next.previousEventId)
        assertEquals(EventModerationStatus.PENDING, next.moderationStatus)
        assertTrue(data.events.registrations.value.none { it.eventId == next.id })
        assertEquals(old.startsAt, data.events.findEvent(old.id)!!.startsAt)
    }
}
