package com.eventoscomunitarios.app.domain.model

/** Dates represent local calendar days, including both selected boundaries. */
data class EventSearchOptions(
    val fromDate: Long? = null,
    val untilDate: Long? = null,
    val todayOnly: Boolean = false,
    val freeOnly: Boolean = false,
    val availableOnly: Boolean = false,
    val outdoorOnly: Boolean = false,
    val sortOrder: EventSortOrder = EventSortOrder.DATE
) {
    fun withDate(field: EventDateField, date: Long): EventSearchOptions = when (field) {
        EventDateField.FROM -> copy(fromDate = date, untilDate = untilDate?.coerceAtLeast(date), todayOnly = false)
        EventDateField.UNTIL -> copy(untilDate = date, fromDate = fromDate?.coerceAtMost(date), todayOnly = false)
    }
}

enum class EventSortOrder { DATE, DISTANCE }
enum class EventDateField { FROM, UNTIL }
