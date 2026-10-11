package com.eventoscomunitarios.app.features.auth

import android.util.Patterns
import com.eventoscomunitarios.app.domain.model.User

/** Campos obligatorios del perfil; la foto y el teléfono son opcionales en el diagrama. */
fun User.validationErrors(): Map<String, String> = buildMap {
    if (id.isBlank()) put("id", "Falta el identificador del usuario")
    if (name.isBlank()) put("name", "Falta el nombre del usuario")
    if (email.isBlank()) {
        put("email", "Falta el correo del usuario")
    } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
        put("email", "El correo del usuario no es válido")
    }
    if (registrationDate.time <= 0 || registrationDate.time > System.currentTimeMillis()) {
        put("registrationDate", "La fecha de registro no es válida")
    }
    // El enum no admite un rol nulo ni valores fuera de los tres roles del diagrama.
    if (phoneNumber != null && (phoneNumber.isBlank() ||
            !Patterns.PHONE.matcher(phoneNumber).matches() ||
            phoneNumber.count { it.isDigit() } !in 7..15)) {
        put("phoneNumber", "El teléfono del usuario no es válido")
    }
}
