package com.eventoscomunitarios.app

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eventoscomunitarios.app.core.data.FakeEvents
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.domain.model.EventRegistrationStatus
import com.eventoscomunitarios.app.features.create_event.CreateEventViewModel
import com.eventoscomunitarios.app.features.edit_event.EditEventViewModel
import com.eventoscomunitarios.app.features.event_detail.EventDetailViewModel
import com.eventoscomunitarios.app.features.my_events.MyEventsViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventViewModelTest {
    private val originalEvents = FakeEvents.events
    private val originalRegistrations = FakeEvents.registrations

    @After
    fun restoreFixtures() {
        FakeEvents.events = originalEvents
        FakeEvents.registrations = originalRegistrations
    }

    @Test
    fun organizerCreatesEventWithOptionalFieldsEmpty() {
        val viewModel = validCreateViewModel("organizer-carlos")

        viewModel.publishEvent()

        assertEquals(RequestResult.Success, viewModel.uiState.value.publishResult)
        val created = FakeEvents.events.last()
        assertEquals("organizer-carlos", created.organizerId)
        assertEquals("", created.description)
        assertNull(created.photoUri)
        assertEquals(0, created.price)
    }

    @Test
    fun requiredCreateFieldsAndOrganizerRoleAreEnforced() {
        val emptyForm = CreateEventViewModel(SavedStateHandle(mapOf("userId" to "organizer-carlos")))
        emptyForm.publishEvent()
        assertFalse(emptyForm.uiState.value.isFormValid)
        assertTrue(emptyForm.uiState.value.titleError != null)
        assertTrue(emptyForm.uiState.value.capacityError != null)

        val participantForm = validCreateViewModel("participant-ana")
        val eventCount = FakeEvents.events.size
        participantForm.publishEvent()
        assertTrue(participantForm.uiState.value.publishResult is RequestResult.Failure)
        assertEquals(eventCount, FakeEvents.events.size)
    }

    @Test
    fun onlyOwnerCanEditAnEvent() {
        val denied = EditEventViewModel(
            SavedStateHandle(mapOf("userId" to "participant-ana", "eventId" to 1))
        )
        assertTrue(denied.uiState.value.loadResult is RequestResult.Failure)

        val owner = EditEventViewModel(
            SavedStateHandle(mapOf("userId" to "organizer-carlos", "eventId" to 1))
        )
        owner.onTitleChange("Título actualizado")
        owner.saveChanges()
        assertEquals(RequestResult.Success, owner.uiState.value.saveResult)
        assertEquals("Título actualizado", FakeEvents.events.first { it.id == 1 }.title)
    }

    @Test
    fun myEventsUsesOnlyCurrentUserRegistrationsAndCreations() {
        val participant = MyEventsViewModel(SavedStateHandle(mapOf("userId" to "participant-ana")))
        assertEquals(setOf(1, 2, 3), participant.uiState.value.enrolledEvents.map { it.event.id }.toSet())
        assertTrue(participant.uiState.value.createdEvents.isEmpty())

        val organizer = MyEventsViewModel(SavedStateHandle(mapOf("userId" to "organizer-carlos")))
        assertTrue(organizer.uiState.value.enrolledEvents.isEmpty())
        assertEquals(
            FakeEvents.events.map { it.id }.toSet(),
            organizer.uiState.value.createdEvents.map { it.id }.toSet()
        )
    }

    @Test
    fun cancellingEnrollmentRemovesOnlyCurrentUserAndUpdatesAttendance() {
        val before = FakeEvents.events.first { it.id == 1 }.attendeeCount
        val viewModel = MyEventsViewModel(SavedStateHandle(mapOf("userId" to "participant-ana")))

        viewModel.cancelEnrollment(1)

        assertFalse(FakeEvents.registrations.any { it.userId == "participant-ana" && it.eventId == 1 })
        assertEquals(before - 1, FakeEvents.events.first { it.id == 1 }.attendeeCount)
    }

    @Test
    fun eventDetailUsesEventDataAndDoesNotDuplicateRegistration() {
        val viewModel = EventDetailViewModel(
            SavedStateHandle(mapOf("userId" to "participant-ana", "eventId" to 1))
        )
        assertEquals(FakeEvents.events.first { it.id == 1 }, viewModel.uiState.value.event)
        assertEquals(EventRegistrationStatus.CONFIRMED, viewModel.uiState.value.registrationStatus)
        val registrationsBefore = FakeEvents.registrations.size

        assertTrue(viewModel.confirmAttendance())

        assertEquals(registrationsBefore, FakeEvents.registrations.size)
    }

    @Test
    fun fullEventAddsNewUserToWaitlistWithoutIncreasingAttendance() {
        val fullEvent = FakeEvents.events.first().copy(capacity = FakeEvents.events.first().attendeeCount)
        FakeEvents.events = listOf(fullEvent) + FakeEvents.events.drop(1)
        val viewModel = EventDetailViewModel(
            SavedStateHandle(mapOf("userId" to "administrator-laura", "eventId" to fullEvent.id))
        )

        assertTrue(viewModel.confirmAttendance())

        assertEquals(EventRegistrationStatus.WAITLIST, viewModel.uiState.value.registrationStatus)
        assertEquals(fullEvent.attendeeCount, FakeEvents.events.first().attendeeCount)
        assertTrue(FakeEvents.registrations.any {
            it.userId == "administrator-laura" &&
                it.eventId == fullEvent.id &&
                it.status == EventRegistrationStatus.WAITLIST
        })
    }

    private fun validCreateViewModel(userId: String) =
        CreateEventViewModel(SavedStateHandle(mapOf("userId" to userId))).apply {
            onTitleChange("Encuentro comunitario")
            onCategoryChange("Comunidad")
            onDateChange("30/09/2026")
            onTimeChange("4:00 p.m.")
            onAddressChange("Salón comunal")
            onCapacityChange("25")
        }
}
