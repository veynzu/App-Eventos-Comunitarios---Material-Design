package com.eventoscomunitarios.app.core.data

import com.eventoscomunitarios.app.domain.model.*
import com.eventoscomunitarios.app.domain.repository.ContentAnalysisRepository
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** El APK llama a Functions. La credencial de Gemini permanece exclusivamente en el servidor. */
@Singleton
class FirebaseContentAnalysisRepository @Inject constructor(
    private val functions: FirebaseFunctions
) : ContentAnalysisRepository {
    private suspend fun call(name: String, data: Map<String, Any?> = emptyMap()): Map<*, *> =
        functions.getHttpsCallable(name).withTimeout(70, TimeUnit.SECONDS).call(data).await().data.objectValue()

    override suspend fun status(): AiPreparationStatus = with(call("aiStatus")) {
        require(number("contractVersion").toInt() == 1) { "Versión del servicio de IA no compatible." }
        AiPreparationStatus(boolean("enabled"), boolean("modelConfigured"), boolean("appCheckEnforced"),
            boolean("preparationOnly"), string("region"), number("contractVersion").toInt())
    }

    override suspend fun analyzeText(text: String, language: String): TextAssessment =
        call("analyzeText", mapOf("text" to text, "language" to language)).textAssessment()

    override suspend fun analyzeEventDraft(request: EventAnalysisRequest, language: String): EventAssessment {
        val response = call("analyzeEventDraft", with(request) {
            mapOf("title" to title, "description" to description, "category" to category.name,
                "latitude" to coordinates.latitude, "longitude" to coordinates.longitude,
                "startsAt" to startsAt, "endsAt" to endsAt, "imagePath" to imagePath,
                "sourceEventId" to sourceEventId, "language" to language)
        })
        val image = response["image"].objectValue()
        require(image.containsKey("relevant") && (image["relevant"] == null || image["relevant"] is Boolean)) { INVALID_RESPONSE }
        return EventAssessment(
            text = response["text"].objectValue().textAssessment(),
            image = ImageAssessment(image["relevant"] as Boolean?, image.string("reason")),
            possibleDuplicates = response.list("possibleDuplicates").map { item -> item.objectValue().let {
                PossibleDuplicate(it.number("eventId").toInt(), it.string("reason"))
            } },
            candidates = response.list("candidates").map { item -> item.objectValue().let {
                DuplicateCandidate(it.number("eventId").toInt(), it.string("title"), it.number("startsAt").toLong(), it.number("distanceKm").toDouble())
            } },
            candidatesTruncated = response.boolean("candidatesTruncated")
        )
    }
}

private const val INVALID_RESPONSE = "El servicio de IA devolvió una respuesta inválida."
private fun Any?.objectValue(): Map<*, *> = requireNotNull(this as? Map<*, *>) { INVALID_RESPONSE }
private fun Map<*, *>.boolean(key: String) = requireNotNull(this[key] as? Boolean) { INVALID_RESPONSE }
private fun Map<*, *>.string(key: String) = requireNotNull(this[key] as? String) { INVALID_RESPONSE }
private fun Map<*, *>.number(key: String) = requireNotNull(this[key] as? Number) { INVALID_RESPONSE }
private fun Map<*, *>.list(key: String) = requireNotNull(this[key] as? List<*>) { INVALID_RESPONSE }
private fun Map<*, *>.textAssessment(): TextAssessment {
    val allowed = boolean("allowed")
    val reasons = list("reasons").map { requireNotNull(it as? String) { INVALID_RESPONSE } }
    require(containsKey("suggestedText") && (this["suggestedText"] == null || this["suggestedText"] is String)) { INVALID_RESPONSE }
    val suggestion = this["suggestedText"] as String?
    require(allowed || (reasons.isNotEmpty() && !suggestion.isNullOrBlank())) { INVALID_RESPONSE }
    return TextAssessment(allowed, reasons, suggestion)
}
