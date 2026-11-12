package com.eventoscomunitarios.app.core.data

import com.eventoscomunitarios.app.domain.model.*
import com.eventoscomunitarios.app.domain.repository.ContentAnalysisRepository

/** Respuestas explícitas para pruebas. Nunca aprueba contenido automáticamente sin un proveedor. */
class InMemoryContentAnalysisRepository(
    var preparationStatus: AiPreparationStatus = AiPreparationStatus(false, false, false, true, "us-central1", 1),
    var textResult: TextAssessment? = null,
    var eventResult: EventAssessment? = null
) : ContentAnalysisRepository {
    override suspend fun status() = preparationStatus
    override suspend fun analyzeText(text: String, language: String): TextAssessment =
        checkNotNull(textResult) { "El análisis con IA todavía no está configurado." }
    override suspend fun analyzeEventDraft(request: EventAnalysisRequest, language: String): EventAssessment =
        checkNotNull(eventResult) { "El análisis con IA todavía no está configurado." }
}
