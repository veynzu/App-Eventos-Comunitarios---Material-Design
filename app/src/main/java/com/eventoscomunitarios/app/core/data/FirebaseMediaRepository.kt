package com.eventoscomunitarios.app.core.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.eventoscomunitarios.app.domain.repository.MediaRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Singleton
class FirebaseMediaRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth
) : MediaRepository {
    override suspend fun uploadImage(userId: String, localUri: String, folder: String): String {
        require(auth.currentUser?.uid == userId) { "Inicia sesión para subir una imagen." }
        require(folder in setOf("events", "profiles", "identities", "event-drafts")) { "Destino de imagen inválido." }
        val uri = Uri.parse(localUri)
        require(uri.scheme == "content") { "Selecciona una imagen desde el dispositivo." }
        val (bytes, mimeType) = withContext(Dispatchers.IO) {
            val content = requireNotNull(context.contentResolver.openInputStream(uri)) { "No se pudo abrir la imagen seleccionada." }
            val data = content.use { stream ->
                val result = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_IMAGE_BYTES) { "La imagen debe pesar como máximo 10 MB." }
                    result.write(buffer, 0, read)
                }
                result.toByteArray()
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outMimeType?.startsWith("image/") == true) {
                "El archivo seleccionado no es una imagen compatible."
            }
            data to bounds.outMimeType
        }
        val reference = storage.reference.child("$folder/$userId/${UUID.randomUUID()}")
        if (folder == "event-drafts") require(mimeType in setOf("image/jpeg", "image/png", "image/webp")) {
            "Selecciona una imagen JPEG, PNG o WebP de hasta 10 MB."
        }
        val metadata = StorageMetadata.Builder().setContentType(mimeType)
            .setCustomMetadata("ownerId", userId).build()
        val upload = reference.putBytes(bytes, metadata)
        try {
            upload.await()
            // Los documentos de identidad se leen con el SDK autenticado, sin publicar downloadUrl.
            return if (folder == "event-drafts") reference.path.trimStart('/')
            else if (folder == "identities") "gs://${reference.bucket}/${reference.path.trimStart('/')}"
            else reference.downloadUrl.await().toString()
        } catch (error: Exception) {
            upload.cancel()
            // Si la descarga de la URL o la pantalla falla, no conservar una imagen sin documento.
            withContext(NonCancellable) {
                try { withTimeout(5_000) { reference.delete().await() } } catch (_: Exception) { /* Reintento manual posible. */ }
            }
            if (error is CancellationException) throw error
            throw error
        }
    }

    override suspend fun deleteImage(url: String) {
        if (url.isBlank()) return
        val reference = if (url.startsWith("event-drafts/")) storage.reference.child(url) else storage.imageReference(url)
        require(reference.bucket == storage.reference.bucket &&
            reference.path.trimStart('/').substringBefore('/') in setOf("events", "profiles", "identities", "event-drafts", "validated-events")) {
            "La imagen no pertenece al almacenamiento de la aplicación."
        }
        try {
            reference.delete().await()
        } catch (error: StorageException) {
            if (error.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw error
        }
    }

    override suspend fun uploadAnalysisImage(userId: String, uri: String): String {
        require(auth.currentUser?.uid == userId) { "Inicia sesión para subir una imagen." }
        if (uri.startsWith("content://")) return uploadImage(userId, uri, "event-drafts")
        val source = storage.imageReference(uri)
        require(source.bucket == storage.reference.bucket &&
            source.path.trimStart('/').substringBefore('/') in setOf("events", "validated-events")) {
            "La imagen no pertenece a un evento de la aplicación. Selecciona otra imagen."
        }
        val bytes = source.getBytes(MAX_IMAGE_BYTES.toLong()).await()
        val mimeType = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outMimeType in setOf("image/jpeg", "image/png", "image/webp")) {
                "Selecciona una imagen JPEG, PNG o WebP de hasta 10 MB."
            }
            bounds.outMimeType
        }
        val target = storage.reference.child("event-drafts/$userId/${UUID.randomUUID()}")
        val upload = target.putBytes(bytes, StorageMetadata.Builder().setContentType(mimeType).setCustomMetadata("ownerId", userId).build())
        try { upload.await(); return target.path.trimStart('/') }
        catch (error: Exception) {
            upload.cancel()
            withContext(NonCancellable) { runCatching { withTimeout(5_000) { target.delete().await() } } }
            throw error
        }
    }

    override suspend fun readPrivateImage(path: String): ByteArray {
        require(path.startsWith("gs://")) { "Referencia de documento inválida" }
        val reference = storage.getReferenceFromUrl(path)
        require(reference.bucket == storage.reference.bucket && reference.path.startsWith("/identities/")) {
            "El documento no pertenece al almacenamiento de la aplicación"
        }
        return reference.getBytes(MAX_IMAGE_BYTES.toLong()).await()
    }

    private companion object { const val MAX_IMAGE_BYTES = 10 * 1024 * 1024 }
}
