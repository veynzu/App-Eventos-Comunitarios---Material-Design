package com.eventoscomunitarios.app.domain.usecase

import com.eventoscomunitarios.app.domain.model.Coordinates
import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.EventCategory
import com.eventoscomunitarios.app.domain.model.EventModerationStatus
import com.eventoscomunitarios.app.domain.model.EventSearchOptions
import com.eventoscomunitarios.app.domain.model.EventSortOrder
import java.util.Calendar

/** Shared by the feed and map so combined search criteria have the same meaning. */
fun filterEvents(
    events: List<Event>,
    origin: Coordinates?,
    radiusKm: Double,
    options: EventSearchOptions = EventSearchOptions(),
    category: EventCategory? = null,
    query: String = "",
    todayOnly: Boolean = false,
    favoriteIds: Set<Int>? = null,
    upcomingOnly: Boolean = false,
    now: Long = System.currentTimeMillis()
): List<Event> {
    if (origin == null || !origin.isValid || !radiusKm.isFinite() || radiusKm <= 0) return emptyList()
    if (options.fromDate != null && options.untilDate != null && options.fromDate > options.untilDate) return emptyList()
    val from = options.fromDate?.let(::eventSearchDayStart)
    val until = options.untilDate?.let(::eventSearchNextDay)
    val today = eventSearchDayStart(now)
    val tomorrow = eventSearchNextDay(now)
    val search = query.trim()
    val matches = events.mapNotNull { event ->
        event.coordinates?.let { event.copy(distanceKm = it.distanceKmTo(origin)) }
    }.filter { event ->
        val ended = event.isFinished || event.endsAt <= now
        !event.isDeleted && event.moderationStatus == EventModerationStatus.APPROVED &&
            event.distanceKm <= radiusKm &&
            (!upcomingOnly || !ended) &&
            (category == null || event.category == category) &&
            (search.isBlank() || event.title.contains(search, true) || event.location.contains(search, true)) &&
            (from == null || event.endsAt > from) &&
            (until == null || event.startsAt < until) &&
            (!(todayOnly || options.todayOnly) || (event.startsAt < tomorrow && event.endsAt > today)) &&
            (!options.freeOnly || event.isFree) &&
            (!options.availableOnly || (!ended && event.startsAt > now && event.attendeeCount < event.capacity)) &&
            (!options.outdoorOnly || event.isOutdoor) &&
            (favoriteIds == null || event.id in favoriteIds)
    }
    val ordering = compareBy<Event> { it.isFinished || it.endsAt <= now }
    return matches.sortedWith(when (options.sortOrder) {
        EventSortOrder.DATE -> ordering.thenBy { it.startsAt }.thenBy { it.distanceKm }.thenBy { it.id }
        EventSortOrder.DISTANCE -> ordering.thenBy { it.distanceKm }.thenBy { it.startsAt }.thenBy { it.id }
    })
}

fun eventSearchDayStart(instant: Long): Long = Calendar.getInstance().apply {
    timeInMillis = instant
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun eventSearchNextDay(instant: Long): Long = Calendar.getInstance().apply {
    timeInMillis = eventSearchDayStart(instant)
    add(Calendar.DAY_OF_YEAR, 1)
}.timeInMillis
