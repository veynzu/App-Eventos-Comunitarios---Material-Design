package com.eventoscomunitarios.app

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.core.util.eventFormDate
import com.eventoscomunitarios.app.core.util.eventFormTime
import com.eventoscomunitarios.app.domain.model.EventModerationStatus
import com.eventoscomunitarios.app.domain.model.EventRegistrationStatus
import com.eventoscomunitarios.app.features.create_event.CreateEventViewModel
import com.eventoscomunitarios.app.features.edit_event.EditEventViewModel
import com.eventoscomunitarios.app.features.event_detail.EventDetailViewModel
import com.eventoscomunitarios.app.features.my_events.MyEventsViewModel
import org.junit.Assert.*
import kotlinx.coroutines.CompletableDeferred
import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.User
import com.eventoscomunitarios.app.domain.repository.EventRepository
import com.eventoscomunitarios.app.domain.usecase.EventUseCases
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventViewModelTest {
    private val data = TestDependencies()
    private fun handle(user: String, event: Int = 1) = SavedStateHandle(mapOf("userId" to user, "eventId" to event))

    @Test fun participantCreatesPendingEventWithRealStartAndEnd() = runBlocking {
        val form = validForm("participant-ana")
        form.publishEvent()
        await { form.uiState.value.publishResult != RequestResult.Loading }
        assertEquals(RequestResult.Success, form.uiState.value.publishResult)
        val event = data.events.events.value.last()
        assertEquals("participant-ana", event.organizerId)
        assertEquals(EventModerationStatus.PENDING, event.moderationStatus)
        assertTrue(event.endsAt > event.startsAt)
        assertNull(event.photoUri)
    }

    @Test fun impossibleDateAndEndBeforeStartDoNotPublish() = runBlocking {
        val form = validForm("organizer-carlos")
        val before = data.events.events.value.size
        form.onDateChange("31/02/2027")
        form.publishEvent()
        assertNotNull(form.uiState.value.dateError)
        assertEquals(before, data.events.events.value.size)
        val reversed = validForm("organizer-carlos")
        reversed.onEndDateChange("01/01/2020")
        reversed.publishEvent()
        assertNotNull(reversed.uiState.value.endDateError)
        assertEquals(before, data.events.events.value.size)
    }

    @Test fun editingRequiresOwnershipAndReturnsToModeration() = runBlocking {
        val denied = EditEventViewModel(handle("participant-ana"), data.events, data.users)
        assertTrue(denied.uiState.value.loadResult is RequestResult.Failure)
        val owner = EditEventViewModel(handle("organizer-carlos"), data.events, data.users)
        owner.onTitleChange("Título actualizado")
        owner.saveChanges()
        await { owner.uiState.value.saveResult != RequestResult.Loading }
        assertEquals(RequestResult.Success, owner.uiState.value.saveResult)
        assertEquals(EventModerationStatus.PENDING, data.events.findEvent(1)?.moderationStatus)
    }

    @Test fun myEventsContainsOnlyCurrentUserEnrollments() = runBlocking {
        val mine = MyEventsViewModel(handle("participant-ana"), data.events, data.users)
        assertEquals(setOf(1, 2, 3), mine.uiState.value.enrolledEvents.map { it.event.id }.toSet())
        assertTrue(mine.uiState.value.createdEvents.isEmpty())
        mine.cancelEnrollment(1)
        await { !mine.uiState.value.isSaving }
        assertFalse(data.events.registrations.value.any { it.userId == "participant-ana" && it.eventId == 1 })
        assertEquals(0, data.events.findEvent(1)?.attendeeCount)
    }

    @Test fun repeatedAttendanceNeverDuplicatesRegistration() = runBlocking {
        val detail = EventDetailViewModel(handle("participant-ana"), data.events, data.users, data.community)
        val before = data.events.registrations.value.size
        detail.confirmAttendance()
        await { !detail.uiState.value.isSaving }
        assertEquals(before, data.events.registrations.value.size)
        assertEquals(EventRegistrationStatus.CONFIRMED, detail.uiState.value.registrationStatus)
    }

    @Test fun cancellationPromotesWaitingParticipantAndCreatesNotification() = runBlocking {
        val waiting = EventDetailViewModel(handle("participant-ana", 3), data.events, data.users, data.community)
        assertEquals(EventRegistrationStatus.WAITLIST, waiting.uiState.value.registrationStatus)
        val confirmed = EventDetailViewModel(handle("participant-laura", 3), data.events, data.users, data.community)
        confirmed.cancelAttendance()
        await { !confirmed.uiState.value.isSaving }
        assertEquals(EventRegistrationStatus.CONFIRMED,
            data.events.registrations.value.first { it.userId == "participant-ana" && it.eventId == 3 }.status)
        assertEquals(1, data.events.findEvent(3)?.attendeeCount)
        assertTrue(data.community.notifications.value.any { it.userId == "participant-ana" && it.eventId == 3 })
    }

    @Test fun keyboardOrPickerCallbackDuringUploadCannotSubmitTheEventTwice() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val delayedRepository = object : EventRepository by data.eventRepository {
            override suspend fun create(actor: User, event: Event): Event {
                gate.await()
                return data.eventRepository.create(actor, event)
            }
        }
        val cases = EventUseCases(delayedRepository, data.userRepository, data.communityRepository)
        val form = validForm("organizer-carlos", cases)
        val initialCount = cases.events.value.size
        form.publishEvent()
        assertEquals(RequestResult.Loading, form.uiState.value.publishResult)

        // Simula callbacks pendientes del IME y del selector durante una escritura remota.
        form.onTitleChange("Callback pendiente")
        form.onPhotoSelected("content://pending-image")
        form.publishEvent()
        assertEquals(RequestResult.Loading, form.uiState.value.publishResult)
        assertEquals("Encuentro comunitario", form.uiState.value.title)
        assertNull(form.uiState.value.photoUri)

        gate.complete(Unit)
        await { form.uiState.value.publishResult != RequestResult.Loading }
        assertEquals(RequestResult.Success, form.uiState.value.publishResult)
        assertEquals(initialCount + 1, cases.events.value.size)
        form.onTitleChange("Callback después del éxito")
        form.publishEvent()
        assertEquals(RequestResult.Success, form.uiState.value.publishResult)
        assertEquals(initialCount + 1, cases.events.value.size)
    }

    private fun validForm(user: String, cases: EventUseCases = data.events): CreateEventViewModel {
        val start = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L
        val end = start + 2 * 60 * 60 * 1000L
        return CreateEventViewModel(handle(user), cases, data.users).apply {
            onTitleChange("Encuentro comunitario")
            onCategoryChange("Comunidad")
            onDateChange(eventFormDate(start)); onTimeChange(eventFormTime(start))
            onEndDateChange(eventFormDate(end)); onEndTimeChange(eventFormTime(end))
            onDescriptionChange("Actividad abierta para compartir con la comunidad.")
            onAddressChange("Salón comunal")
            onCapacityChange("25")
        }
    }
    private suspend fun await(condition: () -> Boolean) = withTimeout(5_000) {
        while (!condition()) delay(10)
    }

}
