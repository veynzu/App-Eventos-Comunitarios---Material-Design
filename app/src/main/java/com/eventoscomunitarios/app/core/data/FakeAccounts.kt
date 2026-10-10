package com.eventoscomunitarios.app.core.data

import com.eventoscomunitarios.app.domain.model.AccountStatus
import com.eventoscomunitarios.app.domain.model.AppUser
import com.eventoscomunitarios.app.domain.model.UserRole

/**
 * Registro y autenticación simulados para cuentas creadas desde el formulario.
 * Las cuentas se conservan solo mientras el proceso de la aplicación está activo.
 */
object FakeAccounts {

    private data class RegisteredAccount(
        val user: AppUser,
        val password: String
    )

    private val registeredAccounts = mutableListOf<RegisteredAccount>()

    fun register(
        user: AppUser,
        password: String
    ): Boolean {
        val emailAlreadyExists = FakeUsers.users.any { existingUser ->
            existingUser.email.equals(user.email, ignoreCase = true)
        } || registeredAccounts.any { account ->
            account.user.email.equals(user.email, ignoreCase = true)
        }

        if (emailAlreadyExists) return false

        registeredAccounts.add(
            RegisteredAccount(
                user = user,
                password = password
            )
        )

        return true
    }

    fun authenticate(
        email: String,
        password: String
    ): AppUser? {
        val registeredAccount = registeredAccounts.firstOrNull { account ->
            account.user.email.equals(email, ignoreCase = true) &&
                    account.password == password
        }

        if (registeredAccount != null) {
            return registeredAccount.user
        }

        // Compatibilidad con los usuarios de prueba que ya tenía FakeUsers.
        val predefinedUser = FakeUsers.users.firstOrNull { user ->
            user.email.equals(email, ignoreCase = true) &&
                    user.password == password
        } ?: return null

        return AppUser(
            fullName = nameFromEmail(predefinedUser.email),
            email = predefinedUser.email,
            phone = "",
            role = UserRole.CLIENT,
            cedula = false,
            accountStatus = AccountStatus.CREATED
        )
    }

    private fun nameFromEmail(email: String): String {
        return email
            .substringBefore("@")
            .replace('.', ' ')
            .replace('_', ' ')
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.replaceFirstChar { character -> character.uppercase() }
            }
            .ifBlank { "Usuario" }
    }
}