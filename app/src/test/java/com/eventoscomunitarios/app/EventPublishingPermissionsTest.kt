package com.eventoscomunitarios.app

import com.eventoscomunitarios.app.core.data.InMemoryCommunityRepository
import com.eventoscomunitarios.app.core.data.InMemoryEventRepository
import com.eventoscomunitarios.app.core.data.InMemoryUserRepository
import com.eventoscomunitarios.app.domain.model.AccountStatus
import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.OrganizerVerificationStatus
import com.eventoscomunitarios.app.domain.repository.EventRepository
import com.eventoscomunitarios.app.domain.usecase.EventUseCases
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EventPublishingPermissionsTest {
    private val users = InMemoryUserRepository()
    private val repository = InMemoryEventRepository()
    private val events = EventUseCases(repository, users, InMemoryCommunityRepository())
    private val organizer = "organizer-carlos"
    private val participant = "participant-ana"
    private val admin = "administrator-laura"

    private fun draft(): Event = repository.events.value.first().copy(
        id = 0, startsAt = System.currentTimeMillis() + 86_400_000,
        endsAt = System.currentTimeMillis() + 90_000_000)

    @Test fun participantsCannotPublishOrReserveIdentifiers() = runBlocking {
        var reserved = false
        val tracked = object : EventRepository by repository {
            override suspend fun nextId(): Int { reserved = true; return repository.nextId() }
        }
        val cases = EventUseCases(tracked, users, InMemoryCommunityRepository())
        assertFalse(cases.canPublish(participant))
        assertTrue(cases.create(participant, draft()).isFailure)
        assertFalse(reserved)
    }

    @Test fun onlyVerifiedOrganizersAndActiveAdminsCanCreate() = runBlocking {
        val original = users.users.value.first { it.id == organizer }
        OrganizerVerificationStatus.entries.forEach { status ->
            users.save(original.copy(organizerVerificationStatus = status))
            assertEquals(status == OrganizerVerificationStatus.VERIFIED, events.canPublish(organizer))
            if (status != OrganizerVerificationStatus.VERIFIED) assertTrue(events.create(organizer, draft()).isFailure)
        }
        assertTrue(events.canPublish(admin))
        assertTrue(events.create(admin, draft()).isSuccess)
        users.save(users.users.value.first { it.id == admin }.copy(accountStatus = AccountStatus.SUSPENDED))
        assertFalse(events.canPublish(admin))
        assertTrue(events.create(admin, draft()).isFailure)
    }

    @Test fun losingVerificationPreventsEditingAndRelaunchingOwnedEvents() = runBlocking {
        val source = events.findEvent(1)!!
        repository.save(source.copy(startsAt = 1, endsAt = 2, isFinished = true))
        assertTrue(events.canRelaunch(organizer, events.findEvent(1)!!))
        users.save(users.users.value.first { it.id == organizer }.copy(
            organizerVerificationStatus = OrganizerVerificationStatus.REJECTED))
        assertFalse(events.canRelaunch(organizer, events.findEvent(1)!!))
        assertTrue(events.relaunch(organizer, 1, draft()).isFailure)
        assertTrue(events.update(organizer, draft().copy(id = 2)).isFailure)
    }
}
