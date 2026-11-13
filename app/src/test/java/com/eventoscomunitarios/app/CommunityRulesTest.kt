package com.eventoscomunitarios.app

import com.eventoscomunitarios.app.core.data.InMemoryCommunityRepository
import com.eventoscomunitarios.app.core.data.InMemoryEventRepository
import com.eventoscomunitarios.app.core.data.InMemoryUserRepository
import com.eventoscomunitarios.app.domain.model.*
import com.eventoscomunitarios.app.domain.repository.CommunityRepository
import com.eventoscomunitarios.app.domain.usecase.CommunityUseCases
import com.eventoscomunitarios.app.domain.usecase.EventUseCases
import com.eventoscomunitarios.app.domain.usecase.UserUseCases
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Before
import org.junit.Test

class CommunityRulesTest {
    private lateinit var eventsRepository: InMemoryEventRepository
    private lateinit var usersRepository: InMemoryUserRepository
    private lateinit var communityRepository: InMemoryCommunityRepository
    private lateinit var events: EventUseCases
    private lateinit var users: UserUseCases
    private lateinit var community: CommunityUseCases
    private val ana = "participant-ana"
    private val laura = "participant-laura"
    private val admin = "administrator-laura"
    private val carlos = "organizer-carlos"

    @Before fun setUp() {
        eventsRepository = InMemoryEventRepository()
        usersRepository = InMemoryUserRepository()
        communityRepository = InMemoryCommunityRepository()
        events = EventUseCases(eventsRepository, usersRepository, communityRepository)
        users = UserUseCases(usersRepository, eventsRepository, communityRepository, events)
        community = CommunityUseCases(communityRepository, eventsRepository, usersRepository)
    }

    private fun draft() = events.events.value.first().copy(
        id = 0, title = " Nuevo encuentro ", startsAt = System.currentTimeMillis() + 86_400_000L,
        endsAt = System.currentTimeMillis() + 90_000_000L, capacity = 2, attendeeCount = 999,
        moderationStatus = EventModerationStatus.APPROVED, isFinished = true, rejectionReason = "anterior"
    )

    private suspend fun published(authorId: String = carlos): Event {
        // Las pruebas sociales pueden usar otra autora, siempre verificada para publicar.
        if (authorId != carlos) usersRepository.save(users.findUser(authorId)!!.copy(
            role = UserRole.ORGANIZER, organizerVerificationStatus = OrganizerVerificationStatus.VERIFIED))
        val created = events.create(authorId, draft()).getOrThrow()
        events.moderate(admin, created.id, EventModerationStatus.APPROVED).getOrThrow()
        return events.findEvent(created.id)!!
    }

    private suspend fun participated(userId: String, eventId: Int = 1) {
        if (events.registrations.value.none { it.userId == userId && it.eventId == eventId }) events.register(userId, eventId).getOrThrow()
        val event = events.findEvent(eventId)!!
        eventsRepository.save(event.copy(startsAt = System.currentTimeMillis() - 7200000, endsAt = System.currentTimeMillis() - 1000, isFinished = true))
    }

    @Test fun creationUsesCurrentAuthorAndAlwaysRequiresModeration() = runBlocking {
        val created = events.create(carlos, draft()).getOrThrow()
        assertEquals(carlos, created.organizerId)
        assertEquals("Nuevo encuentro", created.title)
        assertEquals(EventModerationStatus.PENDING, created.moderationStatus)
        assertEquals(0, created.attendeeCount)
        assertFalse(created.isFinished)
        assertNull(created.rejectionReason)
        assertTrue(community.notifications.value.any { it.userId == admin && it.eventId == created.id })
        assertFalse(events.canView(laura, created))
        assertTrue(events.canView(carlos, created))
    }

    @Test fun invalidDatesAndMissingDescriptionDoNotCreatePublications() = runBlocking {
        val before = events.events.value.size
        assertTrue(events.create(carlos, draft().copy(endsAt = 1L)).isFailure)
        assertTrue(events.create(carlos, draft().copy(startsAt = 1L)).isFailure)
        assertTrue(events.create(carlos, draft().copy(description = " ")).isFailure)
        assertTrue(events.create(carlos, draft().copy(capacity = 0)).isFailure)
        assertTrue(events.create(carlos, draft().copy(price = -1)).isFailure)
        assertEquals(before, events.events.value.size)
    }

    @Test fun rejectionRequiresModeratorAndReasonAndNotifiesAuthor() = runBlocking {
        val event = events.create(carlos, draft()).getOrThrow()
        assertTrue(events.moderate(laura, event.id, EventModerationStatus.APPROVED).isFailure)
        assertTrue(events.moderate(admin, event.id, EventModerationStatus.REJECTED, "  ").isFailure)
        events.moderate(admin, event.id, EventModerationStatus.REJECTED, "Completa la dirección").getOrThrow()
        assertEquals("Completa la dirección", events.findEvent(event.id)?.rejectionReason)
        assertTrue(community.notifications.value.any { it.userId == carlos && it.message.contains("Completa la dirección") })
        assertTrue(events.moderate(admin, event.id, EventModerationStatus.APPROVED).isFailure)
    }

    @Test fun editingResubmitsPreservesOwnerAndProtectsConfirmedCapacity() = runBlocking {
        val event = published()
        events.register(ana, event.id).getOrThrow()
        events.register(laura, event.id).getOrThrow()
        assertTrue(events.update(carlos, event.copy(capacity = 1)).isFailure)
        assertTrue(events.update(ana, event.copy(title = "Cambio ajeno")).isFailure)
        val updated = events.update(carlos, event.copy(organizerId = ana, attendeeCount = 400, title = "Cambio válido")).getOrThrow()
        assertEquals(carlos, updated.organizerId)
        assertEquals(2, updated.attendeeCount)
        assertEquals(EventModerationStatus.PENDING, updated.moderationStatus)
        assertTrue(events.canView(ana, updated))
        assertFalse(events.canRegister(ana, updated))
        assertTrue(events.cancelRegistration(ana, event.id).isSuccess)
    }

    @Test fun registrationIsUniqueAndQueuePromotesInArrivalOrder() = runBlocking {
        // Evento 3: cupo único ocupado por Laura, Ana espera primero.
        assertEquals(EventRegistrationStatus.WAITLIST, events.register(admin, 3).getOrThrow())
        assertEquals(EventRegistrationStatus.WAITLIST, events.register(admin, 3).getOrThrow())
        assertEquals(1, events.registrations.value.count { it.userId == admin && it.eventId == 3 })
        events.cancelRegistration(laura, 3).getOrThrow()
        assertEquals(EventRegistrationStatus.CONFIRMED, events.registrations.value.first { it.userId == ana && it.eventId == 3 }.status)
        assertEquals(EventRegistrationStatus.WAITLIST, events.registrations.value.first { it.userId == admin && it.eventId == 3 }.status)
        events.cancelRegistration(ana, 3).getOrThrow()
        assertEquals(EventRegistrationStatus.CONFIRMED, events.registrations.value.first { it.userId == admin && it.eventId == 3 }.status)
        assertEquals(1, events.findEvent(3)?.attendeeCount)
        assertTrue(community.notifications.value.any { it.userId == admin && it.title.contains("cupo") })
    }

    @Test fun authorAndSuspendedAccountsCannotTakeACupo() = runBlocking {
        assertTrue(events.register(carlos, 1).isFailure)
        assertTrue(events.register("participant-diego", 1).isFailure)
        assertTrue(events.register(laura, 4).isFailure)
        assertTrue(events.register(laura, 8).isFailure)
        assertEquals(1, events.findEvent(1)?.attendeeCount)
    }

    @Test fun onlyOwnerOrModeratorCanFinishAndItMustHaveStarted() = runBlocking {
        assertTrue(events.finish(carlos, 1).isFailure)
        assertTrue(events.finish(ana, 8).isFailure)
        events.finish(carlos, 8).getOrThrow()
        assertTrue(events.findEvent(8)!!.isFinished)
        assertTrue(events.finish(admin, 8).isFailure)
        assertTrue(events.update(carlos, events.findEvent(8)!!).isFailure)
    }

    @Test fun votesAreUniqueChangeableRemovableAndCannotBeSelfVotes() = runBlocking {
        val event = published(laura)
        community.vote(ana, event.id, 1).getOrThrow()
        assertEquals(1, users.statistics(laura).receivedVotes)
        community.vote(ana, event.id, -1).getOrThrow()
        assertEquals(-1, users.statistics(laura).receivedVotes)
        assertEquals(0, users.statistics(laura).reputation)
        assertEquals(1, community.votes.value.size)
        community.vote(ana, event.id, -1).getOrThrow()
        assertTrue(community.votes.value.isEmpty())
        assertTrue(community.vote(laura, event.id, 1).isFailure)
        assertTrue(community.vote(admin, event.id, 5).isFailure)
    }

    @Test fun likesAndDislikesNotifyOnlyTheEventOwnerAndRemovingThemDoesNotNotify() = runBlocking {
        val event = events.findEvent(1)!!
        val actor = users.findUser(ana)!!
        community.vote(ana, event.id, 1).getOrThrow()
        val like = community.notifications.value.single()
        assertEquals(event.organizerId, like.userId)
        assertEquals("Nuevo like", like.title)
        assertEquals("${actor.name} le dio like a ${event.title}.", like.message)
        assertEquals(event.id, like.eventId)
        assertFalse(like.isRead)

        community.vote(ana, event.id, 1).getOrThrow()
        assertEquals(1, community.notifications.value.size)
        community.vote(ana, event.id, -1).getOrThrow()
        val dislike = community.notifications.value.last()
        assertEquals("Nuevo dislike", dislike.title)
        assertEquals("${actor.name} le dio dislike a ${event.title}.", dislike.message)
        assertEquals(event.organizerId, dislike.userId)
        assertEquals(event.id, dislike.eventId)

        community.vote(ana, event.id, 1).getOrThrow()
        assertEquals("Nuevo like", community.notifications.value.last().title)
        community.vote(ana, event.id, -1).getOrThrow()
        community.vote(ana, event.id, -1).getOrThrow()
        assertTrue(community.votes.value.isEmpty())
        assertEquals(4, community.notifications.value.size)
        assertTrue(community.notifications.value.all { it.userId == event.organizerId })
    }

    @Test fun invalidVotesDoNotCreateNotifications() = runBlocking {
        assertTrue(community.vote(carlos, 1, 1).isFailure)
        assertTrue(community.vote(ana, 1, 0).isFailure)
        assertTrue(community.vote("participant-diego", 1, -1).isFailure)
        assertTrue(community.vote(ana, 4, 1).isFailure)
        assertTrue(community.vote(ana, Int.MAX_VALUE, 1).isFailure)
        assertTrue(community.votes.value.isEmpty())
        assertTrue(community.notifications.value.isEmpty())
    }

    @Test fun aFailedVoteDoesNotNotifyTheOwner() = runBlocking {
        val failingVotes = object : CommunityRepository by communityRepository {
            override suspend fun toggleVote(userId: String, eventId: Int, value: Int): Int? {
                throw IllegalStateException("No se pudo guardar el voto")
            }
        }
        val useCases = CommunityUseCases(failingVotes, eventsRepository, usersRepository)
        assertTrue(useCases.vote(ana, 1, 1).isFailure)
        assertTrue(community.notifications.value.isEmpty())
    }

    @Test fun aFailedNotificationDoesNotTurnASavedVoteIntoAFailedReaction() = runBlocking {
        val failingNotices = object : CommunityRepository by communityRepository {
            override suspend fun notify(userId: String, title: String, message: String, eventId: Int?) {
                throw IllegalStateException("No se pudo entregar el aviso")
            }
        }
        val useCases = CommunityUseCases(failingNotices, eventsRepository, usersRepository)
        useCases.vote(ana, 1, 1).getOrThrow()
        assertEquals(EventVote(1, ana, 1), community.votes.value.single())
    }

    @Test fun commentsAndContributionsNotifyTheOwnerButNotTheirOwnAuthor() = runBlocking {
        val event = events.findEvent(1)!!
        community.addComment(laura, event.id, "¿Qué debemos llevar?", isContribution = true).getOrThrow()
        val contributionNotice = community.notifications.value.single()
        assertEquals(event.organizerId, contributionNotice.userId)
        assertEquals("Nuevo comentario", contributionNotice.title)
        assertEquals("${users.findUser(laura)!!.name} comentó en ${event.title}.", contributionNotice.message)
        assertEquals(event.id, contributionNotice.eventId)
        assertFalse(contributionNotice.isRead)

        community.addComment(carlos, event.id, "Lleven agua", isContribution = true).getOrThrow()
        assertEquals(1, community.notifications.value.size)
        participated(ana, event.id)
        community.addComment(ana, event.id, "Gracias por el evento").getOrThrow()
        val commentNotice = community.notifications.value.last()
        assertEquals(event.organizerId, commentNotice.userId)
        assertEquals("Nuevo comentario", commentNotice.title)
        assertEquals("${users.findUser(ana)!!.name} comentó en ${event.title}.", commentNotice.message)
        assertEquals(event.id, commentNotice.eventId)
        assertEquals(2, community.notifications.value.size)

        assertTrue(community.addComment(ana, event.id, " ").isFailure)
        assertTrue(community.addComment(laura, event.id, "No participé").isFailure)
        assertEquals(2, community.notifications.value.size)
    }

    @Test fun commentsHaveRealAuthorAndCanBeRemovedByAuthorOrModerator() = runBlocking {
        participated(ana)
        val comment = community.addComment(ana, 1, "  ¡Nos vemos!  ").getOrThrow()
        assertEquals("¡Nos vemos!", comment.text)
        assertEquals(users.findUser(ana)?.name, comment.authorName)
        assertTrue(community.deleteComment(laura, comment.id).isFailure)
        community.deleteComment(admin, comment.id).getOrThrow()
        assertTrue(community.comments.value.isEmpty())
        assertTrue(community.addComment(ana, 1, " ").isFailure)
        assertTrue(community.addComment(ana, 4, "No aprobado").isFailure)
    }

    @Test fun aFailedNotificationDoesNotTurnASavedCommentIntoAFailedPublication() = runBlocking {
        participated(ana)
        val failingNotices = object : CommunityRepository by communityRepository {
            override suspend fun notify(userId: String, title: String, message: String, eventId: Int?) {
                throw IllegalStateException("No se pudo entregar el aviso")
            }
        }
        val useCases = CommunityUseCases(failingNotices, eventsRepository, usersRepository)
        val saved = useCases.addComment(ana, 1, "Comentario guardado").getOrThrow()
        assertEquals(saved, communityRepository.comments.value.single())
    }

    @Test fun coroutineCancellationIsNotSwallowedBySecondaryNotificationHandling() = runBlocking {
        participated(ana)
        val cancelledNotices = object : CommunityRepository by communityRepository {
            override suspend fun notify(userId: String, title: String, message: String, eventId: Int?) {
                throw CancellationException("Pantalla cerrada")
            }
        }
        val useCases = CommunityUseCases(cancelledNotices, eventsRepository, usersRepository)
        var cancellationPropagated = false
        try { useCases.addComment(ana, 1, "El comentario ya se guardó") }
        catch (_: CancellationException) { cancellationPropagated = true }
        assertTrue(cancellationPropagated)
        assertEquals(1, communityRepository.comments.value.size)
    }

    @Test fun profileUpdateChecksUniqueEmailAndKeepsEmailPendingUntilVerified() = runBlocking {
        assertTrue(users.updateProfile(carlos, "Carlos", " ANA.TORRES@CORREO.COM ", null).isFailure)
        users.updateProfile(carlos, "Carlos Actualizado", "nuevo@correo.com", "3105551234").getOrThrow()
        assertEquals("Carlos Actualizado", users.findUser(carlos)?.name)
        assertEquals("nuevo@correo.com", users.findUser(carlos)?.pendingEmail)
        assertEquals("carlos.gomez@correo.com", users.findUser(carlos)?.email)
        assertTrue(users.login("carlos.gomez@correo.com", "Organizador123").isSuccess)
    }

    @Test fun optionalPhoneIsStoredAsNullAndLettersAreRejected() = runBlocking {
        assertTrue(users.updateProfile(ana, "Ana Torres", "ana.torres@correo.com", "abc1234567").isFailure)
        assertNull(users.updateProfile(ana, "Ana Torres", "ana.torres@correo.com", "  ").getOrThrow().phoneNumber)
        val newUser = users.register("Persona Nueva", "nueva@correo.com", "12345678", "", UserRole.PARTICIPANT).getOrThrow()
        assertNull(newUser.phoneNumber)
    }

    @Test fun deletingAccountRemovesRelatedDataAndPromotesWaitlist() = runBlocking {
        val own = published(laura)
        participated(laura)
        community.addComment(laura, 1, "Comentario").getOrThrow()
        community.vote(laura, 1, 1).getOrThrow()
        community.toggleFavorite(laura, 1).getOrThrow()
        community.toggleFollow(laura, carlos).getOrThrow()
        events.register(ana, own.id).getOrThrow()
        users.deleteAccount(laura).getOrThrow()
        assertNull(users.findUser(laura))
        assertTrue(users.login("laura.gomez@correo.com", "Participante123").isFailure)
        assertNull(events.findEvent(own.id))
        assertFalse(events.registrations.value.any { it.userId == laura || it.eventId == own.id })
        assertFalse(community.comments.value.any { it.userId == laura })
        assertFalse(community.votes.value.any { it.userId == laura })
        assertFalse(community.favorites.value.any { it.userId == laura })
        assertFalse(community.following.value.any { it.userId == laura || it.organizerId == laura })
        assertFalse(community.notifications.value.any { it.userId == laura || it.eventId == own.id })
        assertEquals(EventRegistrationStatus.CONFIRMED, events.registrations.value.first { it.eventId == 3 && it.userId == ana }.status)
        assertEquals(1, events.findEvent(3)?.attendeeCount)
    }

    @Test fun deletingPublicationCleansInteractionsAndKeepsNotificationsWithoutBrokenLinks() = runBlocking {
        val event = published()
        participated(ana, event.id)
        community.addComment(ana, event.id, "Me gustó participar").getOrThrow()
        community.vote(ana, event.id, 1).getOrThrow()
        community.toggleFavorite(ana, event.id).getOrThrow()
        assertTrue(events.delete(ana, event.id).isFailure)
        events.delete(admin, event.id).getOrThrow()
        assertNull(events.findEvent(event.id))
        assertFalse(events.registrations.value.any { it.eventId == event.id })
        assertFalse(community.comments.value.any { it.eventId == event.id })
        assertFalse(community.votes.value.any { it.eventId == event.id })
        assertFalse(community.favorites.value.any { it.eventId == event.id })
        assertFalse(community.notifications.value.any { it.eventId == event.id })
        assertTrue(community.notifications.value.any { it.userId == ana && it.title == "Publicación eliminada" })
        assertTrue(events.create(carlos, draft()).getOrThrow().id > event.id)
    }

    @Test fun notificationsArePrivateAndFollowersReceiveApprovedEvents() = runBlocking {
        community.toggleFollow(ana, carlos).getOrThrow()
        val event = published()
        val notification = community.notifications.value.first { it.userId == ana && it.eventId == event.id }
        assertTrue(community.markRead(laura, notification.id).isFailure)
        community.markRead(ana, notification.id).getOrThrow()
        assertTrue(community.notifications.value.first { it.id == notification.id }.isRead)
        community.markAllRead(carlos).getOrThrow()
        assertTrue(community.notifications.value.filter { it.userId == carlos }.all { it.isRead })
    }

    @Test fun suspensionAndRolesAreEnforcedBelowTheUi() = runBlocking {
        assertTrue(users.updateAccountStatus(ana, laura, AccountStatus.SUSPENDED).isFailure)
        assertTrue(users.updateAccountStatus(admin, admin, AccountStatus.SUSPENDED).isFailure)
        users.updateAccountStatus(admin, laura, AccountStatus.SUSPENDED).getOrThrow()
        assertTrue(events.create(laura, draft()).isFailure)
        assertTrue(community.addComment(laura, 1, "No permitido").isFailure)
        assertTrue(community.vote(laura, 1, 1).isFailure)
        assertTrue(users.updateProfile(laura, "Laura", "laura@correo.com", null).isFailure)
        assertTrue(users.register("Nueva persona", "nueva@correo.com", "12345678", null, UserRole.ADMINISTRATOR).isFailure)
    }

    @Test fun seedCountsMatchConfirmedRegistrationsForEveryEvent() = runBlocking {
        events.events.value.forEach { event ->
            assertEquals(events.registrations.value.count { it.eventId == event.id && it.status == EventRegistrationStatus.CONFIRMED }, event.attendeeCount)
            assertTrue(event.attendeeCount <= event.capacity)
            assertTrue(event.endsAt > event.startsAt)
        }
        assertEquals(7, events.events.value.map { it.category }.distinct().size)
    }
}

