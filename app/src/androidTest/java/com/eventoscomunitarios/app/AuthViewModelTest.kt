package com.eventoscomunitarios.app

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import com.eventoscomunitarios.app.domain.model.User
import com.eventoscomunitarios.app.domain.model.RepositorySyncState
import com.eventoscomunitarios.app.domain.repository.UserRepository
import com.eventoscomunitarios.app.domain.usecase.UserUseCases
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.domain.model.UserRole
import com.eventoscomunitarios.app.features.auth.SessionViewModel
import com.eventoscomunitarios.app.features.auth.validationErrors
import com.eventoscomunitarios.app.features.home.HomeViewModel
import com.eventoscomunitarios.app.features.login.LoginViewModel
import com.eventoscomunitarios.app.features.registro.RegistroViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthViewModelTest {
    private val data = TestDependencies()

    @Test fun demoProfilesAreValidAndCoverAllRoles() = onMain {
        assertEquals(UserRole.entries.toSet(), data.users.users.value.map { it.role }.toSet())
        data.users.users.value.forEach { assertTrue(it.validationErrors().isEmpty()) }
    }

    @Test fun loginNormalizesEmailAndRejectsSuspendedAccounts() = onMain {
        val login = LoginViewModel(data.users)
        login.onEmailChange("  ANA.TORRES@CORREO.COM  ")
        login.onPasswordChange("12345678")
        login.login()
        assertEquals(RequestResult.Success, login.uiState.value.loginResult)
        assertEquals("participant-ana", login.uiState.value.authenticatedUser?.id)
        login.resetForm()
        login.onEmailChange("diego.ramirez@correo.com")
        login.onPasswordChange("Participante123")
        login.login()
        assertTrue(login.uiState.value.loginResult is RequestResult.Failure)
        assertNull(login.uiState.value.authenticatedUser)
    }

    @Test fun malformedAndIncorrectCredentialsNeverAuthenticate() = onMain {
        val login = LoginViewModel(data.users)
        login.login()
        assertNotNull(login.uiState.value.emailError)
        assertNotNull(login.uiState.value.passwordError)
        login.onEmailChange("ana.torres@correo.com")
        login.onPasswordChange("Incorrecta123")
        login.login()
        assertTrue(login.uiState.value.loginResult is RequestResult.Failure)
        assertNull(login.uiState.value.authenticatedUser)
    }

    @Test fun organizerRegistrationWorksWithoutImagesAndCanLogIn() = onMain {
        val register = validRegistration("nueva.persona@correo.com")
        register.onAccountTypeChange(true)
        register.onContinuarClick()
        val user = requireNotNull(register.uiState.value.registeredUser)
        assertEquals(UserRole.ORGANIZER, user.role)
        assertTrue(user.validationErrors().isEmpty())
        val login = LoginViewModel(data.users)
        login.onEmailChange(user.email)
        login.onPasswordChange("ClaveNueva123")
        login.login()
        assertEquals(user, login.uiState.value.authenticatedUser)
    }

    @Test fun duplicateEmailAndInvalidFormNeverCreateAccounts() = onMain {
        val count = data.users.users.value.size
        val duplicate = validRegistration(" ANA.TORRES@CORREO.COM ")
        duplicate.registrar()
        assertTrue(duplicate.uiState.value.registroResult is RequestResult.Failure)
        assertNotNull(duplicate.uiState.value.emailError)
        val invalid = RegistroViewModel(data.users)
        invalid.registrar()
        assertNotNull(invalid.uiState.value.nombreError)
        assertNotNull(invalid.uiState.value.terminosError)
        assertEquals(count, data.users.users.value.size)
    }

    @Test fun homeLoadsExistingUserAndRejectsMissingUser() = onMain {
        val home = HomeViewModel(data.events, data.users, data.community)
        home.loadUser("participant-ana")
        assertEquals(RequestResult.Success, home.uiState.value.loadResult)
        assertTrue(home.uiState.value.canCreateEvent)
        home.loadUser("no-existe")
        assertTrue(home.uiState.value.loadResult is RequestResult.Failure)
        assertNull(home.uiState.value.user)
    }

    @Test fun logoutClearsObservableAndRestorableUserId() = onMain {
        val handle = SavedStateHandle()
        val session = SessionViewModel(handle, data.users)
        session.signIn(data.users.findUser("participant-ana")!!)
        assertEquals("participant-ana", handle.get<String>("userId"))
        session.signOut()
        assertNull(session.uiState.value.userId)
        assertNull(handle.get<String>("userId"))
    }

    @Test fun restoringSessionWaitsForTheProfileInsteadOfSigningOut() = onMain {
        val remoteUsers = MutableStateFlow<List<User>>(emptyList())
        val remoteReady = MutableStateFlow(false)
        val remoteSync = MutableStateFlow(RepositorySyncState(isLoading = true))
        val uid = "participant-ana"
        val remote = object : UserRepository by data.userRepository {
            override val users = remoteUsers
            override val currentUserId = MutableStateFlow<String?>(uid)
            override val sessionReady = remoteReady
            override val syncState = remoteSync
        }
        val useCases = UserUseCases(remote, data.eventRepository, data.communityRepository, data.events)
        val session = SessionViewModel(SavedStateHandle(), useCases)
        assertTrue(session.uiState.value.isLoading)
        assertEquals(uid, session.uiState.value.userId)
        remoteUsers.value = listOf(data.users.findUser(uid)!!)
        remoteSync.value = RepositorySyncState()
        remoteReady.value = true
        val restored = withTimeout(2_000) { session.uiState.first { !it.isLoading } }
        assertEquals(uid, restored.user?.id)
        assertNull(restored.error)
    }

    @Test fun sessionUsesAuthenticatedRepositoryInsteadOfSavedNavigationId() = onMain {
        data.users.login("ana.torres@correo.com", "12345678").getOrThrow()
        val handle = SavedStateHandle(mapOf("userId" to "otro-usuario"))
        val session = SessionViewModel(handle, data.users)
        assertEquals("participant-ana", session.uiState.value.userId)
        assertFalse(session.uiState.value.isLoading)
    }

    private fun onMain(block: suspend () -> Unit) = runBlocking { withContext(Dispatchers.Main.immediate) { block() } }

    private fun validRegistration(email: String) = RegistroViewModel(data.users).apply {
        onNombreChange(" Nueva Persona ")
        onEmailChange(email)
        onTelefonoChange("+57 310 555 1212")
        onPasswordChange("ClaveNueva123")
        onConfirmPasswordChange("ClaveNueva123")
        onTerminosChange(true)
    }
}
