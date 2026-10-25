package com.eventoscomunitarios.app.domain.model


data class Event(
    val id: Int,
    val title: String,
    val dateLabel: String,
    val timeLabel: String,
    val location: String,
    val distanceKm: Double,
    val price: Int,
    val category: EventCategory,
    val attendeeCount: Int,
    val description: String,
    val capacity: Int,
    val organizerId: String,
    val organizerName: String,
    val photoUri: String? = null
) {
    val isFree: Boolean
        get() = price == 0
}

enum class EventCategory {
    SPORTS,
    CULTURE,
    COMMUNITY;

    val displayName: String
        get() = when (this) {
            SPORTS -> "Deportes"
            CULTURE -> "Cultura"
            COMMUNITY -> "Comunidad"
        }
}

data class EventRegistration(
    val userId: String,
    val eventId: Int,
    val status: EventRegistrationStatus
)

enum class EventRegistrationStatus {
    CONFIRMED,
    WAITLIST
}
