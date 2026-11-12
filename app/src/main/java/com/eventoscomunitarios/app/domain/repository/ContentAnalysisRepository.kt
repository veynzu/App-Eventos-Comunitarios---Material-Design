package com.eventoscomunitarios.app.domain.repository

import com.eventoscomunitarios.app.domain.model.*

interface ContentAnalysisRepository {
    suspend fun status(): AiPreparationStatus
    suspend fun analyzeText(text: String, language: String): TextAssessment
    suspend fun analyzeEventDraft(request: EventAnalysisRequest, language: String): EventAssessment
}
