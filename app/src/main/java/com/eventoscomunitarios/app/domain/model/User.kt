package com.eventoscomunitarios.app.domain.model

import java.util.Date

/** Modelo del diagrama; los roles siguen el enum de la guía del profesor. */
data class User(
    val id: String,
    val name: String,
    val email: String,
    val role: UserRole,
    val registrationDate: Date,
    val profilePictureUrl: String? = null,
    // Dato adicional que solicita el formulario actual.
    val phoneNumber: String? = null,
    val accountStatus: AccountStatus = AccountStatus.ACTIVE,
    val pendingEmail: String? = null,
    val organizerVerificationStatus: OrganizerVerificationStatus = if (role == UserRole.ORGANIZER)
        OrganizerVerificationStatus.PENDING else OrganizerVerificationStatus.NOT_REQUIRED
) {
    val canCreateEvents: Boolean get() = accountStatus == AccountStatus.ACTIVE &&
        (role == UserRole.ADMINISTRATOR || (role == UserRole.ORGANIZER &&
            organizerVerificationStatus == OrganizerVerificationStatus.VERIFIED))
    val canPublishEvents: Boolean get() = canCreateEvents
}

enum class AccountStatus {
    ACTIVE,
    SUSPENDED
}

/** La identidad del organizador se aprueba manualmente; no corresponde a verificar el correo. */
enum class OrganizerVerificationStatus { NOT_REQUIRED, PENDING, VERIFIED, REJECTED }

data class OrganizerIdentity(val photoPath: String, val imageBytes: ByteArray)
