package com.eventoscomunitarios.app.features.auth

import com.eventoscomunitarios.app.domain.model.User
import com.eventoscomunitarios.app.domain.model.UserRole
import java.util.Calendar
import java.util.GregorianCalendar

/**
 * Datos completos de prueba compartidos por login y registro.
 * Solo viven en memoria mientras el proceso está activo.
 * La validación, el registro y la autenticación están en los ViewModels.
 */
object FakeUsers {
    val users: MutableList<User> = mutableListOf(
        User(
            id = "participant-ana",
            name = "Ana María Torres",
            email = "ana.torres@correo.com",
            role = UserRole.PARTICIPANT,
            registrationDate = GregorianCalendar(2026, Calendar.AUGUST, 1).time,
            profilePictureUrl = null,
            phoneNumber = "+57 310 555 4821"
        ),
        User(
            id = "organizer-carlos",
            name = "Carlos Gómez",
            email = "carlos.gomez@correo.com",
            role = UserRole.ORGANIZER,
            registrationDate = GregorianCalendar(2026, Calendar.AUGUST, 5).time,
            profilePictureUrl = null,
            phoneNumber = "+57 315 555 4832"
        ),
        User(
            id = "administrator-laura",
            name = "Laura Rojas",
            email = "laura.rojas@correo.com",
            role = UserRole.ADMINISTRATOR,
            registrationDate = GregorianCalendar(2026, Calendar.AUGUST, 8).time,
            profilePictureUrl = null,
            phoneNumber = "+57 320 555 4843"
        )
    )

    // Credenciales de prueba indexadas por el id del perfil.
    val passwords: MutableMap<String, String> = mutableMapOf(
        "participant-ana" to "12345678",
        "organizer-carlos" to "Organizador123",
        "administrator-laura" to "Admin1234"
    )
}
