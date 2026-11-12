package com.eventoscomunitarios.app.features.event_detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventoscomunitarios.app.core.util.toFirebaseMessage
import com.eventoscomunitarios.app.domain.model.AccountStatus
import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.EventComment
import com.eventoscomunitarios.app.domain.model.EventModerationStatus
import com.eventoscomunitarios.app.domain.model.EventRegistration
import com.eventoscomunitarios.app.domain.model.EventRegistrationStatus
import com.eventoscomunitarios.app.domain.model.UserRole
import com.eventoscomunitarios.app.domain.usecase.CommunityUseCases
import com.eventoscomunitarios.app.domain.usecase.EventUseCases
import com.eventoscomunitarios.app.domain.usecase.UserUseCases
import com.eventoscomunitarios.app.domain.usecase.ContentAnalysisUseCases
import com.eventoscomunitarios.app.domain.model.TextAssessment
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

data class EventDetailUiState(
    val event: Event? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val syncError: String? = null,
    val message: String? = null,
    val attendanceConfirmedEventId: Int? = null,
    val isFavorite: Boolean = false,
    val canFavorite: Boolean = false,
    val isFollowing: Boolean = false,
    val canFollow: Boolean = false,
    val registrationStatus: EventRegistrationStatus? = null,
    val canRegister: Boolean = false,
    val canCancelRegistration: Boolean = false,
    val waitlistCount: Int = 0,
    val waitlistPosition: Int? = null,
    val comments: List<EventComment> = emptyList(),
    val commentText: String = "",
    val commentError: String? = null,
    val canComment: Boolean = false,
    val canRelaunch: Boolean = false,
    val canVote: Boolean = false,
    val myVote: Int = 0,
    val positiveVotes: Int = 0,
    val negativeVotes: Int = 0,
    val currentUserId: String = "",
    val isModerator: Boolean = false,
    val commentToDelete: Int? = null,
    val canContribute: Boolean = false,
    val isContribution: Boolean = false,
    val isAnalyzingComment: Boolean = false,
    val textAssessment: TextAssessment? = null
)

@HiltViewModel
class EventDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val events: EventUseCases,
    private val users: UserUseCases,
    private val community: CommunityUseCases,
    private val analysis: ContentAnalysisUseCases
) : ViewModel() {
    private val eventId: Int? = savedStateHandle["eventId"]
    private val userId: String = savedStateHandle["userId"] ?: ""
    private val _uiState = MutableStateFlow(EventDetailUiState(currentUserId = userId))
    val uiState: StateFlow<EventDetailUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch { while (isActive) { delay(30_000); refresh() } }
        viewModelScope.launch {
            merge(
                events.events.map { Unit }, events.registrations.map { Unit },
                users.users.map { Unit }, community.comments.map { Unit },
                community.votes.map { Unit }, community.favorites.map { Unit }, community.following.map { Unit },
                events.syncState.map { Unit }, users.syncState.map { Unit }, community.syncState.map { Unit }
            ).collect { refresh() }
        }
    }

    fun confirmAttendance() {
        val id = eventId ?: return
        launchAction {
            events.register(userId, id).fold(
                onSuccess = { status ->
                    refresh()
                    _uiState.update {
                        it.copy(attendanceConfirmedEventId = id, message = if (status == EventRegistrationStatus.CONFIRMED)
                            "Asistencia confirmada" else "Te agregamos a la lista de espera")
                    }
                }, onFailure = ::showFailure
            )
        }
    }

    fun clearAttendanceNavigation() { _uiState.update { it.copy(attendanceConfirmedEventId = null) } }

    fun cancelAttendance() {
        val id = eventId ?: return
        launchAction {
            events.cancelRegistration(userId, id).fold(
                onSuccess = { refresh(); _uiState.update { it.copy(message = "Inscripción cancelada") } },
                onFailure = ::showFailure
            )
        }
    }

    fun toggleFavorite() {
        val id = eventId ?: return
        launchAction { community.toggleFavorite(userId, id).onSuccess { refresh() }.onFailure(::showFailure) }
    }

    fun toggleFollow() {
        val organizerId = _uiState.value.event?.organizerId ?: return
        launchAction { community.toggleFollow(userId, organizerId).onSuccess { refresh() }.onFailure(::showFailure) }
    }

    fun onCommentChange(value: String) {
        if (!_uiState.value.isSaving) _uiState.update { it.copy(commentText = value, commentError = null, textAssessment = null) }
    }

    fun selectContribution(value: Boolean) {
        if (!_uiState.value.isSaving) _uiState.update { it.copy(isContribution = value, commentError = null, textAssessment = null) }
    }
    fun useSuggestedComment() { _uiState.value.textAssessment?.suggestedText?.takeIf { it.length <= 1000 }?.let(::onCommentChange) }

    fun addComment(language: String = "es") {
        val id = eventId ?: return
        val text = _uiState.value.commentText.trim()
        if (text.isBlank() || text.length > 1000) {
            _uiState.update { it.copy(commentError = if (text.isBlank()) "Escribe un comentario" else "Máximo 1000 caracteres") }
            return
        }
        launchAction {
            val contribution = _uiState.value.isContribution || !_uiState.value.canComment
            if (if (contribution) !_uiState.value.canContribute else !_uiState.value.canComment) return@launchAction
            _uiState.update { it.copy(isAnalyzingComment = true, textAssessment = null, commentError = null) }
            val review = try { analysis.analyzeText(text, language).getOrThrow() }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(commentError = error.toFirebaseMessage()) }
                return@launchAction
            } finally { _uiState.update { it.copy(isAnalyzingComment = false) } }
            _uiState.update { it.copy(textAssessment = review) }
            if (!review.allowed) return@launchAction
            if (review.reviewId == null) {
                _uiState.update { it.copy(commentError = "No se recibió la validación de publicación. Inténtalo de nuevo.") }
                return@launchAction
            }
            community.addComment(userId, id, text, review.reviewId, contribution).fold(
                onSuccess = {
                    refresh()
                    _uiState.update { it.copy(commentText = "", commentError = null, textAssessment = null, message = "Comentario publicado") }
                },
                onFailure = { failure -> _uiState.update { it.copy(commentError = failure.toFirebaseMessage()) } }
            )
        }
    }

    fun requestDeleteComment(commentId: Int) {
        if (!_uiState.value.isSaving) _uiState.update { it.copy(commentToDelete = commentId) }
    }
    fun dismissDeleteComment() { if (!_uiState.value.isSaving) _uiState.update { it.copy(commentToDelete = null) } }

    fun deleteComment() {
        val id = _uiState.value.commentToDelete ?: return
        launchAction {
            community.deleteComment(userId, id).fold(
                onSuccess = { refresh(); _uiState.update { it.copy(commentToDelete = null, message = "Comentario eliminado") } },
                onFailure = { failure -> _uiState.update { it.copy(commentToDelete = null) }; showFailure(failure) }
            )
        }
    }

    fun vote(value: Int) {
        val id = eventId ?: return
        launchAction { community.vote(userId, id, value).onSuccess { refresh() }.onFailure(::showFailure) }
    }

    fun clearMessage() { _uiState.update { it.copy(message = null) } }

    private fun launchAction(action: suspend () -> Unit) {
        if (_uiState.value.isSaving || _uiState.value.isLoading) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { showFailure(failure) }
            finally { _uiState.update { it.copy(isSaving = false) } }
        }
    }

    private fun showFailure(failure: Throwable) { _uiState.update { it.copy(message = failure.toFirebaseMessage()) } }

    private fun refresh() {
        val synchronization = listOf(events.syncState.value, users.syncState.value, community.syncState.value)
        val syncError = synchronization.firstNotNullOfOrNull { it.error }
        if (synchronization.any { it.isLoading } && syncError == null) {
            _uiState.update { it.copy(isLoading = true, error = null, syncError = null) }
            return
        }
        val storedEvent = eventId?.let(events::findEvent)
        val event = storedEvent?.copy(organizerName = users.findUser(storedEvent.organizerId)?.name ?: storedEvent.organizerName)
        val user = users.findUser(userId)
        if (event == null || user == null || !events.canView(userId, event)) {
            _uiState.update { it.copy(event = null, isLoading = false, syncError = syncError, error = syncError ?: "Esta publicación no está disponible") }
            return
        }
        val registration = events.registrations.value.firstOrNull { it.userId == userId && it.eventId == event.id }
        val waitlist = events.registrations.value.filter { it.eventId == event.id && it.status == EventRegistrationStatus.WAITLIST }
            .sortedWith(compareBy<EventRegistration> { it.queueOrder }.thenBy { it.registeredAt })
        val eventVotes = community.votes.value.filter { it.eventId == event.id }
        val canInteract = user.accountStatus == AccountStatus.ACTIVE && event.moderationStatus == EventModerationStatus.APPROVED && !event.isDeleted
        _uiState.update {
            it.copy(
                event = event, isLoading = false, error = null, syncError = syncError,
                isFavorite = community.favorites.value.any { favorite -> favorite.userId == userId && favorite.eventId == event.seriesId },
                canFavorite = canInteract,
                isFollowing = community.following.value.any { follow -> follow.userId == userId && follow.organizerId == event.organizerId },
                canFollow = user.id != event.organizerId,
                registrationStatus = registration?.status,
                canRegister = events.canRegister(userId, event),
                canCancelRegistration = registration != null && !event.isFinished && event.startsAt > System.currentTimeMillis(),
                waitlistCount = waitlist.size,
                waitlistPosition = waitlist.indexOfFirst { pending -> pending.userId == userId }.takeIf { index -> index >= 0 }?.plus(1),
                comments = community.comments.value.filter { comment -> comment.eventId == event.id }
                    .sortedWith(compareBy<EventComment> { comment -> comment.createdAt }.thenBy { comment -> comment.id })
                    .map { comment -> comment.copy(authorName = users.findUser(comment.userId)?.name ?: comment.authorName) },
                canComment = community.canComment(userId, event.id),
                canContribute = community.canContribute(userId, event.id),
                canRelaunch = events.canRelaunch(userId, event),
                canVote = canInteract && user.id != event.organizerId,
                myVote = eventVotes.firstOrNull { vote -> vote.userId == userId }?.value ?: 0,
                positiveVotes = eventVotes.count { vote -> vote.value > 0 },
                negativeVotes = eventVotes.count { vote -> vote.value < 0 },
                isModerator = user.role == UserRole.ADMINISTRATOR
            )
        }
    }
}
