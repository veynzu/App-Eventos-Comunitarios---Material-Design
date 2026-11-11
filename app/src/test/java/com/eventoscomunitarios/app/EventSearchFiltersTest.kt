package com.eventoscomunitarios.app

import com.eventoscomunitarios.app.domain.model.*
import com.eventoscomunitarios.app.domain.usecase.filterEvents
import com.eventoscomunitarios.app.features.home.HomeUiState
import com.eventoscomunitarios.app.features.map.MapUiState
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class EventSearchFiltersTest {
    private val origin = Coordinates(0.0, 0.0)
    private fun day(number: Int, hour: Int = 0): Long = Calendar.getInstance().apply {
        clear(); set(2035, Calendar.JANUARY, number, hour, 0)
    }.timeInMillis
    private fun event(id: Int = 1) = Event(id = id, title = "Community workshop", startsAt = day(10, 12),
        endsAt = day(10, 14), location = "Central park", distanceKm = 999.0, price = 0,
        category = EventCategory.EDUCATION, attendeeCount = 4, description = "Workshop", capacity = 10,
        organizerId = "organizer", organizerName = "Organizer", latitude = 0.0, longitude = 0.01,
        locationConfirmed = true, isOutdoor = true)

    @Test fun combinesDistanceDateCategoryPriceAvailabilityAndText() {
        val accepted = event()
        val candidates = listOf(accepted, event(2).copy(price = 100), event(3).copy(attendeeCount = 10),
            event(4).copy(category = EventCategory.SPORTS), event(5).copy(longitude = 1.0),
            event(6).copy(startsAt = day(12), endsAt = day(13)), event(7).copy(isOutdoor = false),
            event(8).copy(title = "Unrelated", location = "Elsewhere"))
        val result = filterEvents(candidates, origin, 10.0,
            EventSearchOptions(fromDate = day(10), untilDate = day(10), freeOnly = true,
                availableOnly = true, outdoorOnly = true), category = EventCategory.EDUCATION,
            query = " workshop ", now = day(1))
        assertEquals(listOf(1), result.map { it.id })
        assertEquals(1.112, result.single().distanceKm, 0.01)
    }

    @Test fun dateRangeIncludesTheEntireLastLocalDayAndOverlappingEvents() {
        val spanning = event(1).copy(startsAt = day(9), endsAt = day(12))
        val lastEvening = event(2).copy(startsAt = day(10, 23), endsAt = day(11, 1))
        val endsAtStart = event(3).copy(startsAt = day(9), endsAt = day(10))
        val startsAfterEnd = event(4).copy(startsAt = day(11), endsAt = day(12))
        val result = filterEvents(listOf(spanning, lastEvening, endsAtStart, startsAfterEnd), origin, 5.0,
            EventSearchOptions(fromDate = day(10), untilDate = day(10)), now = day(1))
        assertEquals(setOf(1, 2), result.map { it.id }.toSet())
    }

    @Test fun missingOrInvalidOriginNeverUsesAStoredDistance() {
        val events = listOf(event().copy(distanceKm = 0.0))
        assertTrue(filterEvents(events, null, 5.0).isEmpty())
        assertTrue(filterEvents(events, Coordinates(Double.NaN, 0.0), 5.0).isEmpty())
        assertTrue(filterEvents(events, origin, Double.POSITIVE_INFINITY).isEmpty())
        assertTrue(filterEvents(events.map { it.copy(locationConfirmed = false) }, origin, 5.0).isEmpty())
    }

    @Test fun nearestOrderingUsesComputedDistanceAndDateOrderingUsesStartTime() {
        val earlier = event(1).copy(startsAt = day(10, 9), longitude = 0.03, distanceKm = 0.0)
        val nearer = event(2).copy(startsAt = day(10, 15), endsAt = day(10, 16), longitude = 0.01, distanceKm = 999.0)
        assertEquals(listOf(1, 2), filterEvents(listOf(nearer, earlier), origin, 5.0,
            EventSearchOptions(sortOrder = EventSortOrder.DATE), now = day(1)).map { it.id })
        assertEquals(listOf(2, 1), filterEvents(listOf(earlier, nearer), origin, 5.0,
            EventSearchOptions(sortOrder = EventSortOrder.DISTANCE), now = day(1)).map { it.id })
    }

    @Test fun availableSpacesExcludeFinishedFullAndUnapprovedEvents() {
        val candidates = listOf(event(1), event(2).copy(isFinished = true), event(3).copy(attendeeCount = 10),
            event(4).copy(moderationStatus = EventModerationStatus.PENDING), event(5).copy(isDeleted = true),
            event(6).copy(startsAt = day(1), endsAt = day(2)),
            event(7).copy(startsAt = day(5), endsAt = day(6)),
            event(8).copy(startsAt = day(4), endsAt = day(6)))
        assertEquals(listOf(1), filterEvents(candidates, origin, 5.0,
            EventSearchOptions(availableOnly = true), now = day(5)).map { it.id })
    }

    @Test fun customCalendarSelectionKeepsBoundariesInOrder() {
        val original = EventSearchOptions(fromDate = day(10), untilDate = day(12))
        assertEquals(day(15), original.withDate(EventDateField.FROM, day(15)).untilDate)
        assertEquals(day(5), original.withDate(EventDateField.UNTIL, day(5)).fromDate)
        assertTrue(filterEvents(listOf(event()), origin, 5.0,
            original.copy(fromDate = day(15)), now = day(1)).isEmpty())
    }

    @Test fun feedAndMapApplyTheSameCombinedCriteria() {
        val candidates = listOf(event(1), event(2).copy(price = 100), event(3).copy(longitude = 0.1))
        val location = SearchLocation(origin, "Chosen area", SearchLocationSource.SELECTED)
        val filters = EventSearchOptions(fromDate = day(10), untilDate = day(10), freeOnly = true,
            availableOnly = true, sortOrder = EventSortOrder.DISTANCE)
        val home = HomeUiState(events = candidates, searchLocation = location, searchRadiusKm = 5f,
            searchOptions = filters, selectedCategory = EventCategory.EDUCATION)
        val map = MapUiState(events = candidates, searchLocation = location, radiusKm = 5,
            searchOptions = filters, selectedCategory = EventCategory.EDUCATION)
        assertEquals(listOf(1), home.filteredEvents.map { it.id })
        assertEquals(home.filteredEvents, map.visibleEvents)
    }

    @Test fun todayAndFavoritesCombineWithOtherCriteria() {
        val candidates = listOf(event(1), event(2).copy(startsAt = day(11), endsAt = day(12)), event(3))
        assertEquals(listOf(1), filterEvents(candidates, origin, 5.0, EventSearchOptions(freeOnly = true),
            todayOnly = true, favoriteIds = setOf(1, 2), now = day(10, 9)).map { it.id })
    }
}
