package com.eventoscomunitarios.app.domain.model

data class AppUser(
    val fullName: String,
    val email: String,
    val phone: String,
    val role: UserRole,
    val cedula: Boolean,
    val accountStatus: AccountStatus
) {
    val initials: String
        get() = fullName
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { name -> name.first().uppercase() }
            .ifBlank { "US" }
}

enum class UserRole {
    CLIENT,
    ORGANIZER
}

enum class AccountStatus(val value: String) {
    CREATED("creado"),
    PENDING_VERIFICATION("pendiente verificación")
}
