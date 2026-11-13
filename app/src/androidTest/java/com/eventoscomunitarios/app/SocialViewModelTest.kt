package com.eventoscomunitarios.app

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eventoscomunitarios.app.domain.model.EventRegistrationStatus
import com.eventoscomunitarios.app.features.event_detail.EventDetailViewModel
import com.eventoscomunitarios.app.features.notifications.NotificationsViewModel
import com.eventoscomunitarios.app.features.profile.ProfileViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SocialViewModelTest {
    private val data = TestDependencies()
    private val models = ViewModelStore()
    private var sequence = 0

    @After fun clearViewModels() = onMain { models.clear() }

    @Test fun commentsCanBePostedAndModeratedWithNotifications() = onMain {
        finishedParticipation("participant-ana", 1)
        val participant = detail("participant-ana")
        participant.onCommentChange("   ")
        participant.addComment()
        assertNotNull(participant.uiState.value.commentError)
        assertTrue(data.community.comments.value.isEmpty())
        participant.onCommentChange(" ¿Podemos llevar a la familia? ")
        participant.addComment()
        val comment = data.community.comments.value.single()
        assertEquals("¿Podemos llevar a la familia?", comment.text)
        assertEquals("", participant.uiState.value.commentText)

        val notices = tracked(NotificationsViewModel(handle("organizer-carlos"), data.community, data.events, data.users))
        assertEquals(1, notices.uiState.value.unreadCount)
        notices.openNotification(notices.uiState.value.items.single().notification.id)
        assertEquals(1, notices.uiState.value.openEventId)
        assertEquals(0, notices.uiState.value.unreadCount)

        val moderator = detail("administrator-laura")
        moderator.requestDeleteComment(comment.id)
        moderator.deleteComment()
        assertTrue(data.community.comments.value.isEmpty())
        assertTrue(data.community.notifications.value.any { it.userId == "participant-ana" && it.title == "Comentario retirado" })
    }

    @Test fun votesSwitchAndToggleWithoutDuplicatesAndUpdateReputation() = onMain {
        val participant = detail("participant-ana")
        val baseline = data.users.statistics("organizer-carlos").reputation
        participant.vote(1)
        assertEquals(1, participant.uiState.value.positiveVotes)
        assertEquals(baseline + 1, data.users.statistics("organizer-carlos").reputation)
        participant.vote(-1)
        assertEquals(1, data.community.votes.value.size)
        assertEquals(-1, participant.uiState.value.myVote)
        assertEquals(0, participant.uiState.value.positiveVotes)
        participant.vote(-1)
        assertTrue(data.community.votes.value.isEmpty())
        assertEquals(0, participant.uiState.value.myVote)

        val author = detail("organizer-carlos")
        assertFalse(author.uiState.value.canVote)
        author.vote(1)
        assertTrue(data.community.votes.value.isEmpty())
        assertNotNull(author.uiState.value.message)
    }

    @Test fun reactionNotificationsCanBeReadAndOpenTheEventForItsOwner() = onMain {
        val participant = detail("participant-ana")
        participant.vote(1)
        participant.vote(-1)
        participant.vote(-1)

        val notices = tracked(NotificationsViewModel(handle("organizer-carlos"), data.community, data.events, data.users))
        assertEquals(2, notices.uiState.value.unreadCount)
        assertEquals(setOf("Nuevo like", "Nuevo dislike"), notices.uiState.value.items.map { it.notification.title }.toSet())
        assertTrue(notices.uiState.value.items.all { it.canOpenEvent && it.notification.userId == "organizer-carlos" })
        notices.openNotification(notices.uiState.value.items.first().notification.id)
        assertEquals(1, notices.uiState.value.openEventId)
        assertEquals(1, notices.uiState.value.unreadCount)
        notices.markAllRead()
        assertEquals(0, notices.uiState.value.unreadCount)

        val participantNotices = tracked(NotificationsViewModel(handle("participant-ana"), data.community, data.events, data.users))
        assertTrue(participantNotices.uiState.value.items.isEmpty())
    }

    @Test fun profileValidatesFieldsAndUpdatesPublishedAuthorNames() = onMain {
        data.eventRepository.save(data.events.findEvent(2)!!.copy(organizerId = "participant-laura"))
        finishedParticipation("organizer-carlos", 2)
        val comment = data.community.addComment("organizer-carlos", 2, "Los esperamos").getOrThrow()
        val profile = tracked(ProfileViewModel(data.users, data.events, data.community))
        profile.loadUser("organizer-carlos")
        profile.startEditing()
        profile.onEmailChange("correo inválido")
        profile.saveProfile()
        assertNotNull(profile.uiState.value.errors["email"])
        assertEquals("carlos.gomez@correo.com", data.users.findUser("organizer-carlos")?.email)

        profile.onNameChange("Carlos Gómez Actualizado")
        profile.onEmailChange("carlos.actualizado@correo.com")
        profile.onReauthPasswordChange("Organizador123")
        profile.onPhoneChange("")
        profile.saveProfile()
        assertFalse(profile.uiState.value.isEditing)
        assertEquals("Carlos Gómez Actualizado", profile.uiState.value.user?.name)
        assertNull(profile.uiState.value.user?.phoneNumber)
        assertEquals("carlos.gomez@correo.com", profile.uiState.value.user?.email)
        assertEquals("carlos.actualizado@correo.com", profile.uiState.value.user?.pendingEmail)
        val eventDetail = detail("organizer-carlos")
        assertEquals("Carlos Gómez Actualizado", eventDetail.uiState.value.event?.organizerName)
        assertEquals("Carlos Gómez Actualizado", detail("organizer-carlos", 2).uiState.value.comments.first { it.id == comment.id }.authorName)
        assertEquals(1, profile.uiState.value.statistics?.comments)
    }

    @Test fun deletingAccountCleansSocialDataAndPromotesWaitingUser() = onMain {
        finishedParticipation("participant-laura", 2)
        data.community.addComment("participant-laura", 2, "Me interesa asistir").getOrThrow()
        data.community.vote("participant-laura", 2, 1).getOrThrow()
        val profile = tracked(ProfileViewModel(data.users, data.events, data.community))
        profile.loadUser("participant-laura")
        profile.requestDeleteAccount()
        assertTrue(profile.uiState.value.showDeleteConfirmation)
        profile.onDeletePasswordChange("Participante123")
        profile.deleteAccount()
        assertTrue(profile.uiState.value.accountDeleted)
        assertNull(data.users.findUser("participant-laura"))
        assertTrue(data.community.comments.value.none { it.userId == "participant-laura" })
        assertTrue(data.community.votes.value.none { it.userId == "participant-laura" })
        assertTrue(data.events.registrations.value.none { it.userId == "participant-laura" })
        assertEquals(EventRegistrationStatus.CONFIRMED,
            data.events.registrations.value.first { it.userId == "participant-ana" && it.eventId == 3 }.status)
        val notices = tracked(NotificationsViewModel(handle("participant-ana"), data.community, data.events, data.users))
        assertTrue(notices.uiState.value.unreadCount > 0)
        assertTrue(notices.uiState.value.items.all { it.notification.userId == "participant-ana" })
        notices.markAllRead()
        assertEquals(0, notices.uiState.value.unreadCount)
    }

    @Test fun notificationNeverOpensAnEventDeletedAfterTheListWasLoaded() = onMain {
        data.communityRepository.notify("participant-ana", "Cambio de evento", "Revisa el evento", 1)
        val notices = tracked(NotificationsViewModel(handle("participant-ana"), data.community, data.events, data.users))
        val item = notices.uiState.value.items.single()
        assertTrue(item.canOpenEvent)
        data.events.delete("organizer-carlos", 1).getOrThrow()
        notices.openNotification(item.notification.id)
        assertNull(notices.uiState.value.openEventId)
        assertFalse(notices.uiState.value.items.first { it.notification.id == item.notification.id }.canOpenEvent)
        assertTrue(notices.uiState.value.items.first { it.notification.id == item.notification.id }.notification.isRead)
    }

    private fun detail(userId: String, eventId: Int = 1) = tracked(EventDetailViewModel(handle(userId, eventId), data.events, data.users, data.community, data.analysis))
    private suspend fun finishedParticipation(userId: String, eventId: Int) {
        if (data.events.registrations.value.none { it.userId == userId && it.eventId == eventId }) data.events.register(userId, eventId).getOrThrow()
        data.eventRepository.save(data.events.findEvent(eventId)!!.copy(startsAt = System.currentTimeMillis() - 7200000,
            endsAt = System.currentTimeMillis() - 1000, isFinished = true))
    }
    private fun handle(userId: String, eventId: Int = 1) = SavedStateHandle(mapOf("userId" to userId, "eventId" to eventId))
    private fun <T : ViewModel> tracked(viewModel: T): T = viewModel.also { models.put("model-${sequence++}", it) }
    private fun onMain(block: suspend () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync { runBlocking { block() } }
}

