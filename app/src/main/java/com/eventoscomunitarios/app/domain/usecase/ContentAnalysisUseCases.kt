package com.eventoscomunitarios.app.domain.usecase

import com.eventoscomunitarios.app.core.util.firebaseResult
import com.eventoscomunitarios.app.domain.model.EventAnalysisRequest
import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.EventAssessment
import com.eventoscomunitarios.app.domain.repository.ContentAnalysisRepository
import com.eventoscomunitarios.app.domain.repository.MediaRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/** Prepara la imagen privada y conserva el borrador cuando falla el proveedor. */
class ContentAnalysisUseCases @Inject constructor(
    private val repository: ContentAnalysisRepository,
    private val media: MediaRepository? = null
) {
    suspend fun status() = firebaseResult { repository.status() }

    suspend fun analyzeText(text: String, language: String = "es") = firebaseResult {
        require(language in listOf("es", "en")) { "Idioma no soportado." }
        require(text.isNotBlank() && text.trim().length <= 5000) { "Texto vacío o demasiado extenso." }
        repository.analyzeText(text.trim(), language)
    }

    suspend fun analyzeEventDraft(request: EventAnalysisRequest, language: String = "es") = firebaseResult {
        require(language in listOf("es", "en")) { "Idioma no soportado." }
        require(request.coordinates.isValid) { "Confirma una ubicación válida." }
        require(request.title.isNotBlank() && request.title.trim().length <= 200) { "Título inválido." }
        require(request.description.isNotBlank() && request.description.trim().length <= 5000) { "Descripción inválida." }
        require(request.startsAt > System.currentTimeMillis() && request.endsAt > request.startsAt) { "Fechas inválidas." }
        repository.analyzeEventDraft(request.copy(title = request.title.trim(), description = request.description.trim()), language)
    }

    suspend fun reviewEvent(userId: String, event: Event, sourceEventId: Int?, language: String) = firebaseResult {
        var image: String? = null
        try {
            image = event.photoUri?.takeIf { it.isNotBlank() }?.let {
                requireNotNull(media) { "No se puede preparar la imagen." }.uploadAnalysisImage(userId, it)
            }
            analyzeEventDraft(EventAnalysisRequest(event.title, event.description, event.category,
                requireNotNull(event.coordinates), event.startsAt, event.endsAt, image, sourceEventId), language).getOrThrow()
        } finally {
            image?.let { path -> withContext(NonCancellable) { runCatching { withTimeout(5_000) { media?.deleteImage(path) } } } }
        }
    }

    suspend fun discard(review: EventAssessment?) {
        review?.approvedPhotoUri?.let { runCatching { withTimeout(5_000) { media?.deleteImage(it) } } }
    }
}
