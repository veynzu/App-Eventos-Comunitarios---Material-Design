
package com.eventoscomunitarios.app.core.data

import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.EventCategory
import com.eventoscomunitarios.app.domain.model.EventRegistration
import com.eventoscomunitarios.app.domain.model.EventRegistrationStatus
import com.eventoscomunitarios.app.domain.model.EventModerationStatus
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
            ),
            Event(
                id = 4,
                title = "Mercado campesino",
                dateLabel = "Vie 3 oct",
                timeLabel = "7:00 a.m.",
                location = "Plazoleta CAM",
                distanceKm = 1.7,
                price = 0,
                category = EventCategory.CULTURE,
                attendeeCount = 0,
                description = "Mercado local con productos frescos de familias campesinas del Quindío.",
                capacity = 120,
                organizerId = "organizer-jac-villa",
                organizerName = "Junta de Acción Comunal La Villa",
                moderationStatus = EventModerationStatus.PENDING,
                submittedAtLabel = "hace 2 h"
            ),
            Event(
                id = 5,
                title = "Taller de reciclaje",
                dateLabel = "Sáb 4 oct",
                timeLabel = "9:00 a.m.",
                location = "Salón comunal",
                distanceKm = 2.4,
                price = 0,
                category = EventCategory.COMMUNITY,
                attendeeCount = 0,
                description = "Aprende a separar residuos y transformar materiales reutilizables.",
                capacity = 35,
                organizerId = "organizer-carlos",
                organizerName = "Carlos Gómez",
                moderationStatus = EventModerationStatus.PENDING,
                submittedAtLabel = "hace 5 h",
                submittedByName = "Ana María Torres"
            ),
            Event(
                id = 6,
                title = "Caminata nocturna",
                dateLabel = "Sáb 4 oct",
                timeLabel = "6:00 p.m.",
                location = "Vía La Patria",
                distanceKm = 3.2,
                price = 0,
                category = EventCategory.SPORTS,
                attendeeCount = 0,
                description = "Recorrido guiado para disfrutar el paisaje y la actividad física en comunidad.",
                capacity = 45,
                organizerId = "organizer-montana",
                organizerName = "Colectivo Montaña Viva",
                moderationStatus = EventModerationStatus.PENDING,
                submittedAtLabel = "hace 1 d"
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

    fun updateModerationStatus(eventId: Int, status: EventModerationStatus): Event? {
        val current = events.firstOrNull { it.id == eventId } ?: return null
        val updated = current.copy(moderationStatus = status)
        events = events.map { if (it.id == eventId) updated else it }
        return updated
    }
}
