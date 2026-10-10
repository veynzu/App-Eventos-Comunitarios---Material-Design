
package com.eventoscomunitarios.app.core.data

import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.EventCategory

object FakeEvents {

    val events: List<Event> = listOf(
        Event(
            id = 1,
            title = "Torneo de microfútbol",
            dateLabel = "Sáb 27 sep",
            timeLabel = "3:00 p.m.",
            location = "Parque Cafetero",
            distanceKm = 0.8,
            price = 0,
            category = EventCategory.DEPORTE,
            attendeeCount = 42
        ),
        Event(
            id = 2,
            title = "Club de lectura barrial",
            dateLabel = "Dom 28 sep",
            timeLabel = "10:00 a.m.",
            location = "Biblioteca Pública",
            distanceKm = 1.4,
            price = 0,
            category = EventCategory.CULTURA,
            attendeeCount = 18
        ),
        Event(
            id = 3,
            title = "Jornada de reciclaje",
            dateLabel = "Lun 29 sep",
            timeLabel = "8:00 a.m.",
            location = "Salón Comunal La Villa",
            distanceKm = 2.1,
            price = 5000,
            category = EventCategory.COMUNIDAD,
            attendeeCount = 9
        )
    )
}
