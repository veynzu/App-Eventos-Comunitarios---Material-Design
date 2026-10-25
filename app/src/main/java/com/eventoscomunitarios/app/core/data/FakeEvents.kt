
package com.eventoscomunitarios.app.core.data

import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.EventCategory
import com.eventoscomunitarios.app.domain.model.EventRegistration
import com.eventoscomunitarios.app.domain.model.EventRegistrationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FakeEvents {

    private val _events = MutableStateFlow(
        listOf(
            Event(
                id = 1,
                title = "Torneo de microfútbol",
                dateLabel = "Sáb 27 sep",
                timeLabel = "3:00 p.m.",
                location = "Parque Cafetero",
                distanceKm = 0.8,
                price = 0,
                category = EventCategory.SPORTS,
                attendeeCount = 42,
                description = "Torneo abierto para equipos del barrio. Inscribe tu equipo de cinco jugadores.",
                capacity = 60,
                organizerId = "organizer-carlos",
                organizerName = "Carlos Gómez"
            ),
            Event(
                id = 2,
                title = "Club de lectura barrial",
                dateLabel = "Dom 28 sep",
                timeLabel = "10:00 a.m.",
                location = "Biblioteca Pública",
                distanceKm = 1.4,
                price = 0,
                category = EventCategory.CULTURE,
                attendeeCount = 18,
                description = "Encuentro para conversar sobre literatura y compartir nuevas lecturas.",
                capacity = 30,
                organizerId = "organizer-carlos",
                organizerName = "Carlos Gómez"
            ),
            Event(
                id = 3,
                title = "Jornada de reciclaje",
                dateLabel = "Lun 29 sep",
                timeLabel = "8:00 a.m.",
                location = "Salón Comunal La Villa",
                distanceKm = 2.1,
                price = 5000,
                category = EventCategory.COMMUNITY,
                attendeeCount = 9,
                description = "Jornada comunitaria para recolectar y clasificar materiales reciclables.",
                capacity = 50,
                organizerId = "organizer-carlos",
                organizerName = "Carlos Gómez"
            )
        )
    )

    val eventsFlow: StateFlow<List<Event>> = _events.asStateFlow()
    var events: List<Event>
        get() = _events.value
        set(value) {
            _events.value = value
        }

    private val _registrations = MutableStateFlow(
        listOf(
            EventRegistration("participant-ana", 1, EventRegistrationStatus.CONFIRMED),
            EventRegistration("participant-ana", 2, EventRegistrationStatus.CONFIRMED),
            EventRegistration("participant-ana", 3, EventRegistrationStatus.WAITLIST)
        )
    )

    val registrationsFlow: StateFlow<List<EventRegistration>> = _registrations.asStateFlow()
    var registrations: List<EventRegistration>
        get() = _registrations.value
        set(value) {
            _registrations.value = value
        }
}
