package com.eventoscomunitarios.app

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.core.util.eventFormDate
import com.eventoscomunitarios.app.core.util.eventFormTime
import com.eventoscomunitarios.app.domain.model.*
import com.eventoscomunitarios.app.features.create_event.CreateEventViewModel
import com.eventoscomunitarios.app.features.edit_event.EditEventViewModel
import com.eventoscomunitarios.app.features.event_detail.EventDetailViewModel
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Verifica el envío real de los ViewModels con un proveedor determinista, sin consumir Gemini. */
@RunWith(AndroidJUnit4::class)
class AiPublishingFlowTest {
    private val data = TestDependencies()
    private val store = ViewModelStore()
    private var sequence = 0
    private fun <T : ViewModel> track(vm: T): T = vm.also { store.put("ai-${sequence++}", it) }
    @After fun close() = runBlocking { withContext(Dispatchers.Main) { store.clear() } }
    private suspend fun await(done: () -> Boolean) = withTimeout(5_000) { while (!done()) delay(10) }
    private fun form(): CreateEventViewModel = track(CreateEventViewModel(
        SavedStateHandle(mapOf("userId" to "organizer-carlos")), data.events, data.users, data.analysis
    )).apply {
        val start = System.currentTimeMillis() + 7 * 86_400_000L
        onTitleChange("Encuentro comunitario"); onCategoryChange("Comunidad")
        onDateChange(eventFormDate(start)); onTimeChange(eventFormTime(start))
        onEndDateChange(eventFormDate(start + 7_200_000)); onEndTimeChange(eventFormTime(start + 7_200_000))
        onLocationSelected(Coordinates.ARMENIA, "Salón comunal")
        onDescriptionChange("Compartimos ideas para mejorar el barrio."); onCapacityChange("20")
    }
    private fun assessment(text: TextAssessment = TextAssessment(true, emptyList(), null),
        image: ImageAssessment = ImageAssessment(null, ""), duplicates: List<PossibleDuplicate> = emptyList()) =
        EventAssessment(text, image, duplicates,
            duplicates.map { DuplicateCandidate(it.eventId, "Evento existente", System.currentTimeMillis() + 86_400_000, .2) },
            false, if (text.allowed && image.relevant != false) "fixture-review" else null)

    @Test fun rejectedTextKeepsDraftAndSuggestionWithoutCreatingEvent() = runBlocking {
        data.analysisRepository.eventResult = assessment(TextAssessment(false, listOf("Lenguaje ofensivo"), "Invitación respetuosa"))
        val initial = data.events.events.value.size
        val vm = form(); vm.publishEvent()
        await { vm.uiState.value.assessment != null }
        assertEquals(initial, data.events.events.value.size)
        assertEquals("Lenguaje ofensivo", vm.uiState.value.assessment!!.text.reasons.single())
        assertTrue(vm.uiState.value.description.isNotBlank())
        vm.useSuggestedDescription()
        assertEquals("Invitación respetuosa", vm.uiState.value.description)
        assertNull(vm.uiState.value.assessment)
    }

    @Test fun unrelatedImagePreventsPublication() = runBlocking {
        data.analysisRepository.eventResult = assessment(image = ImageAssessment(false, "No corresponde a la categoría"))
        val initial = data.events.events.value.size
        val vm = form(); vm.onPhotoSelected("content://fixture/photo"); vm.publishEvent()
        await { vm.uiState.value.assessment != null }
        assertEquals(initial, data.events.events.value.size)
        assertFalse(vm.uiState.value.assessment!!.image.relevant!!)
    }

    @Test fun duplicatesWaitForExplicitDecisionBeforeSaving() = runBlocking {
        data.analysisRepository.eventResult = assessment(duplicates = listOf(PossibleDuplicate(1, "Misma actividad en la zona")))
        val initial = data.events.events.value.size
        val vm = form(); vm.publishEvent()
        await { vm.uiState.value.showDuplicateDialog }
        assertEquals(initial, data.events.events.value.size)
        vm.confirmDifferentEvent()
        await { vm.uiState.value.publishResult == RequestResult.Success }
        assertEquals(initial + 1, data.events.events.value.size)
        assertTrue(data.events.events.value.last().aiDuplicateConfirmed)
        assertEquals("fixture-review", data.events.events.value.last().aiReviewId)
    }

    @Test fun editingDraftInvalidatesPreviousReviewAndDuplicateConfirmation() = runBlocking {
        data.analysisRepository.eventResult = assessment(duplicates = listOf(PossibleDuplicate(1, "Coincidencia")))
        val vm = form(); vm.publishEvent()
        await { vm.uiState.value.showDuplicateDialog }
        vm.dismissDuplicates(); vm.onTitleChange("Otra actividad")
        assertNull(vm.uiState.value.assessment)
        assertNull(vm.uiState.value.reviewedEvent)
        assertFalse(vm.uiState.value.showDuplicateDialog)
    }

    @Test fun providerFailureOrMissingProofNeverBecomesApproval() = runBlocking {
        data.analysisRepository.eventResult = null
        val initial = data.events.events.value.size
        val vm = form(); vm.publishEvent()
        await { vm.uiState.value.publishResult is RequestResult.Failure }
        assertEquals(initial, data.events.events.value.size)
        assertTrue(vm.uiState.value.description.isNotBlank())
        data.analysisRepository.eventResult = assessment().copy(reviewId = null)
        vm.publishEvent()
        await { vm.uiState.value.publishResult is RequestResult.Failure }
        assertEquals(initial, data.events.events.value.size)
    }

    @Test fun editAlsoAnalyzesBeforeChangingStoredContent() = runBlocking {
        val before = data.events.findEvent(1)!!
        data.analysisRepository.eventResult = assessment(TextAssessment(false, listOf("Revisar lenguaje"), "Nueva propuesta"))
        val vm = track(EditEventViewModel(SavedStateHandle(mapOf("userId" to before.organizerId, "eventId" to 1)), data.events, data.users, data.analysis))
        await { vm.uiState.value.loadResult == RequestResult.Success }
        vm.onDescriptionChange("Contenido que será rechazado"); vm.saveChanges()
        await { vm.uiState.value.assessment != null }
        assertEquals(before.description, data.events.findEvent(1)!!.description)
    }

    @Test fun contributionsAreModeratedAndCanBeAddedWithoutAttendanceReview() = runBlocking {
        val vm = track(EventDetailViewModel(SavedStateHandle(mapOf("userId" to "participant-ana", "eventId" to 1)), data.events, data.users, data.community, data.analysis))
        await { !vm.uiState.value.isLoading }
        assertTrue(vm.uiState.value.canContribute)
        assertFalse(vm.uiState.value.canComment)
        data.analysisRepository.textResult = TextAssessment(false, listOf("Lenguaje ofensivo"), "Hay acceso por la entrada sur.")
        vm.onCommentChange("Texto rechazado"); vm.addComment()
        await { vm.uiState.value.textAssessment != null && !vm.uiState.value.isSaving }
        assertTrue(data.community.comments.value.isEmpty())
        vm.useSuggestedComment()
        assertEquals("Hay acceso por la entrada sur.", vm.uiState.value.commentText)
        data.analysisRepository.textResult = TextAssessment(true, emptyList(), null, "fixture-text-review")
        vm.addComment()
        await { data.community.comments.value.isNotEmpty() }
        assertTrue(data.community.comments.value.single().isContribution)
        assertEquals("fixture-text-review", data.community.comments.value.single().aiReviewId)
    }
}
