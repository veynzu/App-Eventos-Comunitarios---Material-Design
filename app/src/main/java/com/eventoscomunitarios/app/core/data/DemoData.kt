package com.eventoscomunitarios.app.core.data

import com.eventoscomunitarios.app.domain.model.*
import java.util.Calendar
import java.util.GregorianCalendar

/** Fábricas de ejemplos; las colecciones mutables pertenecen a los repositorios, no a objetos globales. */
internal fun demoUsers(): List<User> = listOf(
    demoUser("participant-ana", "Ana María Torres", "ana.torres@correo.com", UserRole.PARTICIPANT, "+57 310 555 4821"),
    demoUser("organizer-carlos", "Carlos Gómez", "carlos.gomez@correo.com", UserRole.ORGANIZER, "+57 315 555 4832"),
    demoUser("administrator-laura", "Laura Rojas", "laura.rojas@correo.com", UserRole.ADMINISTRATOR, "+57 320 555 4843"),
    demoUser("organizer-jac-villa", "Junta A. C. La Villa", "contacto@jaclavilla.org", UserRole.ORGANIZER, "+57 606 741 2001"),
    demoUser("organizer-montana", "Colectivo Montaña", "hola@montanaviva.org", UserRole.ORGANIZER, "+57 311 555 7612"),
    demoUser("participant-diego", "Diego Ramírez", "diego.ramirez@correo.com", UserRole.PARTICIPANT, "+57 300 555 1042", AccountStatus.SUSPENDED),
    demoUser("participant-laura", "Laura Gómez", "laura.gomez@correo.com", UserRole.PARTICIPANT, "+57 312 555 9991"),
    demoUser("participant-jose", "José Andrade", "jose.andrade@correo.com", UserRole.PARTICIPANT, "+57 316 555 2271", AccountStatus.SUSPENDED),
    demoUser("participant-pepelino", "Pepelino Cierra", "pepelino.cierra@correo.com", UserRole.PARTICIPANT, "+57 317 555 3830", AccountStatus.SUSPENDED)
)

private fun demoUser(id: String, name: String, email: String, role: UserRole, phone: String, status: AccountStatus = AccountStatus.ACTIVE) = User(
    id = id, name = name, email = email, role = role,
    registrationDate = GregorianCalendar(2026, Calendar.AUGUST, 1).time,
    phoneNumber = phone, accountStatus = status,
    organizerVerificationStatus = if (role == UserRole.ORGANIZER) OrganizerVerificationStatus.VERIFIED else OrganizerVerificationStatus.NOT_REQUIRED
)

internal fun demoPasswords() = mapOf(
    "participant-ana" to "12345678", "organizer-carlos" to "Organizador123",
    "administrator-laura" to "Admin1234", "organizer-jac-villa" to "Organizador123",
    "organizer-montana" to "Organizador123", "participant-diego" to "Participante123",
    "participant-laura" to "Participante123", "participant-jose" to "Participante123",
    "participant-pepelino" to "Participante123"
)

internal fun demoRegistrations() = listOf(
    EventRegistration("participant-ana", 1, EventRegistrationStatus.CONFIRMED),
    EventRegistration("participant-ana", 2, EventRegistrationStatus.CONFIRMED),
    EventRegistration("participant-laura", 3, EventRegistrationStatus.CONFIRMED),
    EventRegistration("participant-ana", 3, EventRegistrationStatus.WAITLIST)
)

internal fun demoEvents(): List<Event> {
    val now = System.currentTimeMillis()
    val day = 24 * 60 * 60 * 1000L
    fun example(id: Int, title: String, category: EventCategory, days: Int, location: String,
                capacity: Int, description: String, status: EventModerationStatus = EventModerationStatus.APPROVED,
                organizerId: String = "organizer-carlos", organizerName: String = "Carlos Gómez") = Event(
        id = id, title = title, startsAt = now + days * day, endsAt = now + days * day + 2 * 60 * 60 * 1000L,
        location = location, distanceKm = id * 0.4, price = 0, category = category,
        attendeeCount = demoRegistrations().count { it.eventId == id && it.status == EventRegistrationStatus.CONFIRMED },
        description = description, capacity = capacity, organizerId = organizerId, organizerName = organizerName,
        moderationStatus = status, submittedAtLabel = "Ejemplo", latitude = 4.5339 + id * 0.001,
        longitude = -75.6811 + id * 0.001, locationConfirmed = true, isOutdoor = location.contains("Parque")
    )
    return listOf(
        example(1, "Torneo de microfútbol", EventCategory.SPORTS, 3, "Parque Cafetero", 60, "Torneo abierto para equipos del barrio. Inscribe tu equipo de cinco jugadores."),
        example(2, "Club de lectura barrial", EventCategory.CULTURE, 4, "Biblioteca Pública", 30, "Encuentro para conversar sobre literatura y compartir nuevas lecturas."),
        example(3, "Jornada de reciclaje", EventCategory.ENVIRONMENT, 5, "Salón Comunal La Villa", 1, "Jornada comunitaria para recolectar materiales. Cupo limitado: prueba la lista de espera."),
        example(4, "Mercado campesino", EventCategory.COMMUNITY, 6, "Plazoleta CAM", 120, "Productos frescos de familias campesinas del Quindío.", EventModerationStatus.PENDING, "organizer-jac-villa", "Junta A. C. La Villa"),
        example(5, "Taller de refuerzo escolar", EventCategory.EDUCATION, 7, "Salón comunal", 35, "Apoyo escolar comunitario en lectura y matemáticas.", EventModerationStatus.PENDING),
        example(6, "Taller de tecnología", EventCategory.TECHNOLOGY, 8, "Biblioteca Pública", 45, "Aprende herramientas digitales para tu comunidad.", EventModerationStatus.PENDING, "organizer-montana", "Colectivo Montaña"),
        example(7, "Jornada de bienestar", EventCategory.HEALTH, -3, "Centro comunitario", 50, "Encuentro comunitario de hábitos saludables.").copy(isFinished = true),
        example(8, "Encuentro vecinal en curso", EventCategory.COMMUNITY, 0, "Salón comunal", 30, "Reunión en curso para acordar mejoras del barrio. El autor puede finalizarla.")
            .copy(startsAt = now - 60 * 60 * 1000L, endsAt = now + 60 * 60 * 1000L)
    )
}
