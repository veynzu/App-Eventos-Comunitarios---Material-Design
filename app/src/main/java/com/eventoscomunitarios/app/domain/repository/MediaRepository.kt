package com.eventoscomunitarios.app.domain.repository

/** Conserva las direcciones remotas en los modelos, nunca un URI local del dispositivo. */
interface MediaRepository {
    suspend fun uploadImage(userId: String, localUri: String, folder: String): String
    suspend fun deleteImage(url: String)
}
