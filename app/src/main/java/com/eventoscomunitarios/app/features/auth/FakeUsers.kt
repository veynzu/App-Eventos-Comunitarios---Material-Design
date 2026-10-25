package com.eventoscomunitarios.app.features.auth

import com.eventoscomunitarios.app.domain.model.User
import com.eventoscomunitarios.app.domain.model.UserRole
import com.eventoscomunitarios.app.domain.model.AccountStatus
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
        ),
        User(
            id = "organizer-jac-villa",
            name = "Junta A. C. La Villa",
            email = "contacto@jaclavilla.org",
            role = UserRole.ORGANIZER,
            registrationDate = GregorianCalendar(2026, Calendar.JULY, 12).time,
            phoneNumber = "+57 606 741 2001"
        ),
        User(
            id = "organizer-montana",
            name = "Colectivo Montaña",
            email = "hola@montanaviva.org",
            role = UserRole.ORGANIZER,
            registrationDate = GregorianCalendar(2026, Calendar.JULY, 20).time,
            phoneNumber = "+57 311 555 7612"
        ),
        User(
            id = "participant-diego",
            name = "Diego Ramírez",
            email = "diego.ramirez@correo.com",
            role = UserRole.PARTICIPANT,
            registrationDate = GregorianCalendar(2026, Calendar.SEPTEMBER, 2).time,
            phoneNumber = "+57 300 555 1042",
            accountStatus = AccountStatus.SUSPENDED
        ),
        User(
            id = "participant-laura",
            name = "Laura Gómez",
            email = "laura.gomez@correo.com",
            role = UserRole.PARTICIPANT,
            registrationDate = GregorianCalendar(2026, Calendar.SEPTEMBER, 5).time,
            phoneNumber = "+57 312 555 9991"
        ),
        User(
            id = "participant-jose",
            name = "José Andrade",
            email = "jose.andrade@correo.com",
            role = UserRole.PARTICIPANT,
            registrationDate = GregorianCalendar(2026, Calendar.SEPTEMBER, 9).time,
            phoneNumber = "+57 316 555 2271",
            accountStatus = AccountStatus.SUSPENDED
        ),
        User(
            id = "participant-pepelino",
            name = "Pepelino Cierra",
            email = "pepelino.cierra@correo.com",
            role = UserRole.PARTICIPANT,
            registrationDate = GregorianCalendar(2026, Calendar.SEPTEMBER, 11).time,
            phoneNumber = "+57 317 555 3830",
            accountStatus = AccountStatus.SUSPENDED
        )
    )

    // Credenciales de prueba indexadas por el id del perfil.
    val passwords: MutableMap<String, String> = mutableMapOf(
        "participant-ana" to "12345678",
        "organizer-carlos" to "Organizador123",
        "administrator-laura" to "Admin1234",
        "organizer-jac-villa" to "Organizador123",
        "organizer-montana" to "Organizador123",
        "participant-diego" to "Participante123",
        "participant-laura" to "Participante123",
        "participant-jose" to "Participante123",
        "participant-pepelino" to "Participante123"
    )

    fun updateAccountStatus(userId: String, status: AccountStatus): User? {
        val index = users.indexOfFirst { it.id == userId }
        if (index == -1) return null
        val updated = users[index].copy(accountStatus = status)
        users[index] = updated
        return updated
    }
}
