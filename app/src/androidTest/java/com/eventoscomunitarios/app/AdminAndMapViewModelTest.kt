package com.eventoscomunitarios.app

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.domain.model.AccountStatus
import com.eventoscomunitarios.app.domain.model.EventModerationStatus
import com.eventoscomunitarios.app.features.admin_dashboard.AdminDashboardViewModel
import com.eventoscomunitarios.app.features.admin_moderation.AdminModerationViewModel
import com.eventoscomunitarios.app.features.admin_users.AdminUsersViewModel
import com.eventoscomunitarios.app.features.admin_users.UserFilter
import com.eventoscomunitarios.app.features.map.MapViewModel
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdminAndMapViewModelTest {
    private val data = TestDependencies()
    private fun handle(user: String = "administrator-laura") = SavedStateHandle(mapOf("userId" to user))

    @Test fun mapShowsOnlyApprovedActiveEventsWithinRadius() = runBlocking {
        val map = MapViewModel(handle("participant-ana"), data.events, data.users)
        assertEquals(RequestResult.Success, map.uiState.value.loadResult)
        assertTrue(map.uiState.value.visibleEvents.all {
            it.moderationStatus == EventModerationStatus.APPROVED && !it.isFinished && it.distanceKm <= 5
        })
        assertFalse(map.uiState.value.visibleEvents.any { it.id == 4 })
    }

    @Test fun nonModeratorCannotLoadDashboard() = runBlocking {
        val dashboard = AdminDashboardViewModel(handle("participant-ana"), data.events, data.users)
        assertTrue(dashboard.uiState.value.loadResult is RequestResult.Failure)
    }

    @Test fun rejectionRequiresReasonAndNotifiesAuthor() = runBlocking {
        val moderation = AdminModerationViewModel(handle(), data.events, data.users)
        moderation.reject(4)
        moderation.confirmAction()
        assertEquals(EventModerationStatus.PENDING, data.events.findEvent(4)?.moderationStatus)
        assertNotNull(moderation.uiState.value.reasonError)
        moderation.onReasonChange("Falta precisar la actividad y su responsable")
        moderation.confirmAction()
        await { !moderation.uiState.value.isSaving }
        assertEquals(EventModerationStatus.REJECTED, data.events.findEvent(4)?.moderationStatus)
        assertEquals("Falta precisar la actividad y su responsable", data.events.findEvent(4)?.rejectionReason)
        assertTrue(data.community.notifications.value.any { it.userId == "organizer-jac-villa" && it.eventId == 4 })
    }

    @Test fun approvalUpdatesSharedDataAndSuspensionBlocksLogin() = runBlocking {
        val moderation = AdminModerationViewModel(handle(), data.events, data.users)
        moderation.approve(4)
        await { !moderation.uiState.value.isSaving }
        assertEquals(EventModerationStatus.APPROVED, data.events.findEvent(4)?.moderationStatus)
        val users = AdminUsersViewModel(handle(), data.users)
        users.selectFilter(UserFilter.ORGANIZERS)
        assertTrue(users.uiState.value.visibleUsers.all { it.role.name == "ORGANIZER" })
        users.toggleAccountStatus("organizer-carlos")
        await { data.users.findUser("organizer-carlos")?.accountStatus == AccountStatus.SUSPENDED }
        assertEquals(AccountStatus.SUSPENDED, data.users.findUser("organizer-carlos")?.accountStatus)
        assertTrue(data.users.login("carlos.gomez@correo.com", "Organizador123").isFailure)
    }
    private suspend fun await(condition: () -> Boolean) = withTimeout(5_000) {
        while (!condition()) delay(10)
    }

}
