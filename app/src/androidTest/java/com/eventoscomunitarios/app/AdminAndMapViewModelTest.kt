package com.eventoscomunitarios.app

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eventoscomunitarios.app.core.data.FakeEvents
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.domain.model.AccountStatus
import com.eventoscomunitarios.app.domain.model.EventModerationStatus
import com.eventoscomunitarios.app.features.admin_dashboard.AdminDashboardViewModel
import com.eventoscomunitarios.app.features.admin_moderation.AdminModerationViewModel
import com.eventoscomunitarios.app.features.admin_users.AdminUsersViewModel
import com.eventoscomunitarios.app.features.admin_users.UserFilter
import com.eventoscomunitarios.app.features.auth.FakeUsers
import com.eventoscomunitarios.app.features.map.MapViewModel
import com.eventoscomunitarios.app.features.login.LoginViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdminAndMapViewModelTest {
    private val originalEvents = FakeEvents.events
    private val originalUsers = FakeUsers.users.toList()

    @After
    fun restoreFixtures() {
        FakeEvents.events = originalEvents
        FakeUsers.users.clear()
        FakeUsers.users.addAll(originalUsers)
    }

    @Test
    fun clientMapOnlyShowsApprovedEventsWithinRadius() {
        val viewModel = MapViewModel(SavedStateHandle(mapOf("userId" to "participant-ana")))

        assertEquals(RequestResult.Success, viewModel.uiState.value.loadResult)
        assertTrue(viewModel.uiState.value.visibleEvents.all {
            it.moderationStatus == EventModerationStatus.APPROVED && it.distanceKm <= 5
        })
        assertFalse(viewModel.uiState.value.visibleEvents.any { it.id == 4 })
    }

    @Test
    fun nonAdministratorCannotLoadDashboard() {
        val viewModel = AdminDashboardViewModel(SavedStateHandle(mapOf("userId" to "participant-ana")))

        assertTrue(viewModel.uiState.value.loadResult is RequestResult.Failure)
    }

    @Test
    fun administratorCanApprovePendingEvent() {
        val viewModel = AdminModerationViewModel(SavedStateHandle(mapOf("userId" to "administrator-laura")))

        viewModel.approve(4)

        assertEquals(EventModerationStatus.APPROVED, FakeEvents.events.first { it.id == 4 }.moderationStatus)
        assertFalse(viewModel.uiState.value.visibleEvents.any { it.id == 4 })
        val map = MapViewModel(SavedStateHandle(mapOf("userId" to "participant-ana")))
        assertTrue(map.uiState.value.visibleEvents.any { it.id == 4 })
    }

    @Test
    fun usersCanBeFilteredAndSuspended() {
        val viewModel = AdminUsersViewModel(SavedStateHandle(mapOf("userId" to "administrator-laura")))
        viewModel.selectFilter(UserFilter.ORGANIZERS)
        assertTrue(viewModel.uiState.value.visibleUsers.all { it.role.name == "ORGANIZER" })

        viewModel.toggleAccountStatus("organizer-carlos")

        assertEquals(
            AccountStatus.SUSPENDED,
            FakeUsers.users.first { it.id == "organizer-carlos" }.accountStatus
        )
        val login = LoginViewModel().apply {
            onEmailChange("carlos.gomez@correo.com")
            onPasswordChange("Organizador123")
            login()
        }
        assertTrue(login.uiState.value.loginResult is RequestResult.Failure)
    }
}
