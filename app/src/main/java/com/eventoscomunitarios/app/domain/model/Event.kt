package com.eventoscomunitarios.app.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Inicio y fin son instantes reales; las etiquetas se calculan para presentar. */
data class Event(
    val id: Int,
    val title: String,
    val startsAt: Long,
    val endsAt: Long,
    val location: String,
    val distanceKm: Double,
    val price: Int,
    val category: EventCategory,
    val attendeeCount: Int,
    val description: String,
    val capacity: Int,
    val organizerId: String,
    val organizerName: String,
    val photoUri: String? = null,
    val moderationStatus: EventModerationStatus = EventModerationStatus.APPROVED,
    val submittedAtLabel: String = "",
    val submittedByName: String = organizerName,
    val latitude: Double = 4.5339,
    val longitude: Double = -75.6811,
    val isFinished: Boolean = false,
    val rejectionReason: String? = null,
    val isDeleted: Boolean = false,
    val revision: Long = 0,
    val locationConfirmed: Boolean = false,
    val seriesId: Int = id,
    val previousEventId: Int? = null,
    val isOutdoor: Boolean = false,
    val aiReviewId: String? = null,
    val aiDuplicateConfirmed: Boolean = false
) {
    val coordinates: Coordinates? get() = Coordinates(latitude, longitude).takeIf { locationConfirmed && it.isValid }
    val hasEnded: Boolean get() = isFinished || endsAt <= System.currentTimeMillis()
    val isFree: Boolean
        get() = price == 0
    val dateLabel: String get() = SimpleDateFormat("dd/MM/yyyy", Locale("es", "CO")).format(Date(startsAt))
    val timeLabel: String get() = SimpleDateFormat("HH:mm", Locale("es", "CO")).format(Date(startsAt))
    val endDateLabel: String get() = SimpleDateFormat("dd/MM/yyyy", Locale("es", "CO")).format(Date(endsAt))
    val endTimeLabel: String get() = SimpleDateFormat("HH:mm", Locale("es", "CO")).format(Date(endsAt))
}

enum class EventCategory {
    SPORTS,
    CULTURE,
    COMMUNITY,
    EDUCATION,
    ENVIRONMENT,
    HEALTH,
    TECHNOLOGY;

    val displayName: String
        get() = when (this) {
            SPORTS -> "Deportes"
            CULTURE -> "Cultura"
            COMMUNITY -> "Comunidad"
            EDUCATION -> "Educación"
            ENVIRONMENT -> "Medio ambiente"
            HEALTH -> "Salud"
            TECHNOLOGY -> "Tecnología"
        }
}

data class EventRegistration(
    val userId: String,
    val eventId: Int,
    val status: EventRegistrationStatus,
    val registeredAt: Long = System.currentTimeMillis(),
    val queueOrder: Long = 0
)

enum class EventRegistrationStatus {
    CONFIRMED,
    WAITLIST
}

enum class EventModerationStatus {
    PENDING,
    APPROVED,
    REJECTED
}

/** Un evento y sus inscripciones se modifican juntos, también en varios dispositivos. */
data class EventRecord(val event: Event, val registrations: List<EventRegistration>)
data class EventChange(val before: EventRecord, val after: EventRecord?)
