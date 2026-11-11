package com.eventoscomunitarios.app

import com.eventoscomunitarios.app.core.data.InMemoryCommunityRepository
import com.eventoscomunitarios.app.core.data.InMemoryEventRepository
import com.eventoscomunitarios.app.core.data.InMemoryUserRepository
import com.eventoscomunitarios.app.domain.model.AccountStatus
import com.eventoscomunitarios.app.domain.model.OrganizerVerificationStatus
import com.eventoscomunitarios.app.domain.model.UserRole
import com.eventoscomunitarios.app.domain.repository.MediaRepository
import com.eventoscomunitarios.app.domain.usecase.EventUseCases
import com.eventoscomunitarios.app.domain.usecase.UserUseCases
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OrganizerVerificationTest {
    private val repository = InMemoryUserRepository()
    private val events = InMemoryEventRepository()
    private val community = InMemoryCommunityRepository()
    private var photoNumber = 0
    private val media = object : MediaRepository {
        override suspend fun uploadImage(userId: String, localUri: String, folder: String): String =
            "gs://test-bucket/$folder/$userId/${++photoNumber}"
        override suspend fun deleteImage(url: String) = Unit
        override suspend fun readPrivateImage(path: String): ByteArray = byteArrayOf(1, 2, 3)
    }
    private val users = UserUseCases(repository, events, community, EventUseCases(events, repository, community), media)
    private val admin = "administrator-laura"

    private suspend fun registerOrganizer(email: String = "organizer@example.test") = users.register(
        "Organizador nuevo", email, "Password123", null, UserRole.ORGANIZER, "content://identity/photo"
    ).getOrThrow()

    @Test fun organizerNeedsIdentityAndCannotPublishWhilePending() = runBlocking {
        assertTrue(users.register("Organizador nuevo", "missing@example.test", "Password123", null, UserRole.ORGANIZER).isFailure)
        val organizer = registerOrganizer()
        assertEquals(OrganizerVerificationStatus.PENDING, organizer.organizerVerificationStatus)
        assertFalse(organizer.canCreateEvents)
        assertFalse(users.findUser("participant-ana")!!.canCreateEvents)
        assertTrue(users.findUser(admin)!!.canCreateEvents)
    }

    @Test fun onlyAdministratorCanReadAndApproveIdentity() = runBlocking {
        val organizer = registerOrganizer()
        assertTrue(users.loadOrganizerIdentity("participant-ana", organizer.id).isFailure)
        assertTrue(users.reviewOrganizerIdentity(organizer.id, organizer.id, "content://identity/photo", true).isFailure)
        val identity = users.loadOrganizerIdentity(admin, organizer.id).getOrThrow()
        val verified = users.reviewOrganizerIdentity(admin, organizer.id, identity.photoPath, true).getOrThrow()
        assertEquals(OrganizerVerificationStatus.VERIFIED, verified.organizerVerificationStatus)
        assertTrue(verified.canCreateEvents)
        assertTrue(users.submitOrganizerIdentity(organizer.id, "content://identity/replacement").isFailure)
    }

    @Test fun rejectedIdentityBlocksAccountAndReactivationRequiresReviewAgain() = runBlocking {
        val organizer = registerOrganizer()
        val identity = users.loadOrganizerIdentity(admin, organizer.id).getOrThrow()
        val rejected = users.reviewOrganizerIdentity(admin, organizer.id, identity.photoPath, false).getOrThrow()
        assertEquals(AccountStatus.SUSPENDED, rejected.accountStatus)
        assertEquals(OrganizerVerificationStatus.REJECTED, rejected.organizerVerificationStatus)
        assertTrue(users.login(organizer.email, "Password123").isFailure)
        val reactivated = users.updateAccountStatus(admin, organizer.id, AccountStatus.ACTIVE).getOrThrow()
        assertEquals(OrganizerVerificationStatus.PENDING, reactivated.organizerVerificationStatus)
        assertFalse(reactivated.canCreateEvents)
    }

    @Test fun cannotApproveAnIdentityThatChangedAfterOpeningReview() = runBlocking {
        val organizer = registerOrganizer()
        val original = users.loadOrganizerIdentity(admin, organizer.id).getOrThrow()
        users.submitOrganizerIdentity(organizer.id, "content://identity/replacement").getOrThrow()
        assertTrue(users.reviewOrganizerIdentity(admin, organizer.id, original.photoPath, true).isFailure)
        assertEquals(OrganizerVerificationStatus.PENDING, users.findUser(organizer.id)!!.organizerVerificationStatus)
    }
}
