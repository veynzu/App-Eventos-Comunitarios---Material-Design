package com.eventoscomunitarios.app.domain.model

/** Preparación del contrato: una evaluación no autoriza por sí sola la escritura en Firestore. */
data class AiPreparationStatus(
    val enabled: Boolean,
    val modelConfigured: Boolean,
    val appCheckEnforced: Boolean,
    val preparationOnly: Boolean,
    val region: String,
    val contractVersion: Int
)

data class TextAssessment(
    val allowed: Boolean,
    val reasons: List<String>,
    val suggestedText: String?,
    val reviewId: String? = null
)

data class EventAnalysisRequest(
    val title: String,
    val description: String,
    val category: EventCategory,
    val coordinates: Coordinates,
    val startsAt: Long,
    val endsAt: Long,
    val imagePath: String? = null,
    val sourceEventId: Int? = null
)

data class ImageAssessment(val relevant: Boolean?, val reason: String)
data class PossibleDuplicate(val eventId: Int, val reason: String)
data class DuplicateCandidate(val eventId: Int, val title: String, val startsAt: Long, val distanceKm: Double)
data class EventAssessment(
    val text: TextAssessment,
    val image: ImageAssessment,
    val possibleDuplicates: List<PossibleDuplicate>,
    val candidates: List<DuplicateCandidate>,
    val candidatesTruncated: Boolean,
    val reviewId: String? = null,
    val approvedPhotoUri: String? = null
)
