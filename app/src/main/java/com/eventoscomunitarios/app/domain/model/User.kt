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
    val accountStatus: AccountStatus = AccountStatus.ACTIVE
)

enum class AccountStatus {
    ACTIVE,
    SUSPENDED
}
