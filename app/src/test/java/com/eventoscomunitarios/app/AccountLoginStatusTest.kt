package com.eventoscomunitarios.app

import com.eventoscomunitarios.app.core.data.InMemoryCommunityRepository
import com.eventoscomunitarios.app.core.data.InMemoryEventRepository
import com.eventoscomunitarios.app.core.data.InMemoryUserRepository
import com.eventoscomunitarios.app.domain.model.AccountStatus
import com.eventoscomunitarios.app.domain.model.OrganizerVerificationStatus
import com.eventoscomunitarios.app.domain.repository.UserRepository
import com.eventoscomunitarios.app.domain.usecase.EventUseCases
import com.eventoscomunitarios.app.domain.usecase.UserUseCases
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AccountLoginStatusTest {
    private val repository = InMemoryUserRepository()
    private val events = InMemoryEventRepository()
    private val community = InMemoryCommunityRepository()
    private val eventCases = EventUseCases(events, repository, community)
    private val users = UserUseCases(repository, events, community, eventCases)

    @Test fun suspendedAccountExplainsItsStatusWithoutOpeningASession() = runBlocking {
        val result = users.login("diego.ramirez@correo.com", "Participante123")
        assertEquals("Tu cuenta está suspendida. Contacta al administrador.", result.exceptionOrNull()?.message)
        assertNull(repository.currentUserId.value)
    }

    @Test fun rejectedOrganizerExplainsBlockAndCanLogInAfterAdminReactivation() = runBlocking {
        val organizer = users.findUser("organizer-carlos")!!
        repository.save(organizer.copy(accountStatus = AccountStatus.SUSPENDED,
            organizerVerificationStatus = OrganizerVerificationStatus.REJECTED))
        assertEquals("Tu cuenta está bloqueada. Contacta al administrador.",
            users.login(organizer.email, "Organizador123").exceptionOrNull()?.message)
        assertNull(repository.currentUserId.value)
        users.updateAccountStatus("administrator-laura", organizer.id, AccountStatus.ACTIVE).getOrThrow()
        assertEquals(organizer.id, users.login(organizer.email, "Organizador123").getOrThrow().id)
    }

    @Test fun incorrectCredentialsDoNotRevealAccountStatus() = runBlocking {
        assertEquals("Correo o contraseña incorrectos",
            users.login("diego.ramirez@correo.com", "Incorrecta123").exceptionOrNull()?.message)
    }

    @Test fun useCaseClosesASessionIfARepositoryReturnsASuspendedProfile() = runBlocking {
        users.login("ana.torres@correo.com", "12345678").getOrThrow()
        val restrictedProfile = users.findUser("participant-diego")!!
        val invalid = object : UserRepository by repository {
            override suspend fun login(email: String, password: String) = restrictedProfile
        }
        val cases = UserUseCases(invalid, events, community, eventCases)
        assertTrue(cases.login("diego.ramirez@correo.com", "Participante123").isFailure)
        assertNull(repository.currentUserId.value)
    }
}
