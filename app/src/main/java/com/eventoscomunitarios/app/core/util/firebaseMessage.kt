package com.eventoscomunitarios.app.core.util

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.CancellationException

fun Throwable.toFirebaseMessage(): String = when (this) {
    is FirebaseNetworkException -> "No se pudo conectar. Revisa tu conexión a internet e intenta de nuevo."
    is FirebaseTooManyRequestsException -> "Se realizaron demasiados intentos. Espera unos minutos e intenta de nuevo."
    is FirebaseAuthException -> when (errorCode) {
        "ERROR_EMAIL_ALREADY_IN_USE" -> "Ya existe una cuenta con este correo."
        "ERROR_INVALID_EMAIL" -> "Ingresa un correo válido."
        "ERROR_WEAK_PASSWORD" -> "La contraseña no cumple los requisitos de seguridad."
        "ERROR_USER_DISABLED" -> "Esta cuenta está deshabilitada."
        "ERROR_USER_NOT_FOUND", "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIALS" -> "Correo o contraseña incorrectos."
        "ERROR_REQUIRES_RECENT_LOGIN" -> "Vuelve a iniciar sesión para completar esta operación."
        "ERROR_OPERATION_NOT_ALLOWED" -> "El acceso con correo y contraseña todavía no está habilitado en Firebase."
        "ERROR_TOO_MANY_REQUESTS" -> "Se realizaron demasiados intentos. Intenta más tarde."
        else -> "No se pudo completar la operación de la cuenta. Intenta de nuevo."
    }
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Firestore rechazó el acceso. La operación necesita autorización en las reglas del proyecto."
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> "Inicia sesión para acceder a los datos."
        FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> "No se pudo conectar con Firestore. Revisa tu conexión e intenta de nuevo."
        FirebaseFirestoreException.Code.FAILED_PRECONDITION -> "Firestore no pudo completar la consulta. Revisa la configuración de la base de datos."
        else -> "No se pudo guardar o consultar la información. Intenta de nuevo."
    }
    is StorageException -> when (errorCode) {
        StorageException.ERROR_NOT_AUTHENTICATED -> "Inicia sesión para guardar imágenes."
        StorageException.ERROR_NOT_AUTHORIZED -> "Storage rechazó el acceso a la imagen. Revisa las reglas del proyecto."
        StorageException.ERROR_BUCKET_NOT_FOUND -> "El almacenamiento de imágenes todavía no está disponible en Firebase."
        StorageException.ERROR_QUOTA_EXCEEDED -> "Se alcanzó la cuota de almacenamiento de imágenes."
        StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> "No se pudo subir la imagen. Revisa tu conexión e intenta de nuevo."
        else -> "No se pudo completar la operación con la imagen. Intenta de nuevo."
    }
    else -> message ?: "No se pudo completar la operación. Intenta de nuevo."
}

/** Mantiene la cancelación estructurada de las tareas de los ViewModels. */
suspend fun <T> firebaseResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(IllegalStateException(error.toFirebaseMessage(), error))
}
