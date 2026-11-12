package com.eventoscomunitarios.app.domain.usecase

import com.eventoscomunitarios.app.core.util.firebaseResult
import com.eventoscomunitarios.app.domain.model.EventAnalysisRequest
import com.eventoscomunitarios.app.domain.repository.ContentAnalysisRepository
import javax.inject.Inject

/** Casos de uso preparados; la activación en crear/editar/comentar se hará con Gemini configurado. */
class ContentAnalysisUseCases @Inject constructor(private val repository: ContentAnalysisRepository) {
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
}
