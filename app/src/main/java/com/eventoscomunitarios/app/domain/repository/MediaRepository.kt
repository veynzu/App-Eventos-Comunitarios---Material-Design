package com.eventoscomunitarios.app.domain.repository

/** Conserva las direcciones remotas en los modelos, nunca un URI local del dispositivo. */
interface MediaRepository {
    suspend fun uploadImage(userId: String, localUri: String, folder: String): String
    suspend fun deleteImage(url: String)
    suspend fun readPrivateImage(path: String): ByteArray = error("No se puede cargar el documento de identidad")
    suspend fun uploadAnalysisImage(userId: String, uri: String): String = error("No se puede preparar la imagen del evento")
}
