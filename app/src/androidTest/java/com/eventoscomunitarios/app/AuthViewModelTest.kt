package com.eventoscomunitarios.app

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.domain.model.UserRole
import com.eventoscomunitarios.app.features.auth.FakeUsers
import com.eventoscomunitarios.app.features.auth.SessionViewModel
import com.eventoscomunitarios.app.features.auth.validationErrors
import com.eventoscomunitarios.app.features.home.HomeViewModel
import com.eventoscomunitarios.app.features.login.LoginViewModel
import com.eventoscomunitarios.app.features.registro.RegistroViewModel
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class AuthViewModelTest {
    private val originalUsers = FakeUsers.users.toList()
    private val originalPasswords = FakeUsers.passwords.toMap()

    @After
    fun restoreFixtures() {
        FakeUsers.users.clear()
        FakeUsers.users.addAll(originalUsers)
        FakeUsers.passwords.clear()
        FakeUsers.passwords.putAll(originalPasswords)
    }

    @Test
    fun seededProfilesAreCompleteAndIncludeAllRoles() {
        assertEquals(UserRole.entries.toSet(), FakeUsers.users.map { it.role }.toSet())
        assertEquals(FakeUsers.users.size, FakeUsers.users.map { it.id }.distinct().size)
        FakeUsers.users.forEach { user ->
            assertTrue("${user.id}: ${user.validationErrors()}", user.validationErrors().isEmpty())
            assertNotNull(FakeUsers.passwords[user.id])
        }
    }

    @Test
    fun loginReturnsOriginalCompleteProfileAndIgnoresEmailCase() {
        val user = FakeUsers.users.first()
        val login = LoginViewModel()
        login.onEmailChange("  ${user.email.uppercase()}  ")
        login.onPasswordChange(FakeUsers.passwords.getValue(user.id))
        login.login()
        assertEquals(RequestResult.Success, login.uiState.value.loginResult)
        assertSame(user, login.uiState.value.authenticatedUser)
    }

    @Test
    fun invalidAndIncorrectCredentialsDoNotAuthenticate() {
        val login = LoginViewModel()
        login.login()
        assertNotNull(login.uiState.value.emailError)
        assertNotNull(login.uiState.value.passwordError)
        login.onEmailChange(FakeUsers.users.first().email)
        login.onPasswordChange("Incorrecta123")
        login.login()
        assertTrue(login.uiState.value.loginResult is RequestResult.Failure)
        assertNull(login.uiState.value.authenticatedUser)
    }

    @Test
    fun registrationCreatesCompleteProfileWhichCanLogIn() {
        val register = validRegistration("nueva.persona@correo.com")
        register.registrar()
        val user = requireNotNull(register.uiState.value.registeredUser)
        assertEquals(RequestResult.Success, register.uiState.value.registroResult)
        assertEquals("Nueva Persona", user.name)
        assertEquals(UserRole.PARTICIPANT, user.role)
        assertTrue(user.id.isNotBlank())
        assertTrue(user.validationErrors().isEmpty())
        assertNull(user.profilePictureUrl)
        val login = LoginViewModel()
        login.onEmailChange(user.email)
        login.onPasswordChange("ClaveNueva123")
        login.login()
        assertSame(user, login.uiState.value.authenticatedUser)
    }

    @Test
    fun duplicateEmailIsRejectedWithoutAddingAnotherAccount() {
        val count = FakeUsers.users.size
        val register = validRegistration("  ${FakeUsers.users.first().email.uppercase()}  ")
        register.registrar()
        assertEquals(count, FakeUsers.users.size)
        assertTrue(register.uiState.value.registroResult is RequestResult.Failure)
        assertNotNull(register.uiState.value.emailError)
        assertNull(register.uiState.value.registeredUser)
    }

    @Test
    fun invalidFieldsNeverCreateAnAccount() {
        val count = FakeUsers.users.size
        val register = RegistroViewModel()
        register.registrar()
        with(register.uiState.value) {
            assertNotNull(nombreError)
            assertNotNull(emailError)
            assertNotNull(telefonoError)
            assertNotNull(passwordError)
            assertNotNull(confirmPasswordError)
            assertNotNull(terminosError)
            assertNull(registeredUser)
        }
        val malformed = validRegistration("malformado@correo.com")
        malformed.onTelefonoChange("abc")
        malformed.onPasswordChange("sinmayusculas")
        malformed.onConfirmPasswordChange("diferente")
        malformed.registrar()
        assertFalse(malformed.uiState.value.isFormValid)
        assertEquals(count, FakeUsers.users.size)
    }

    @Test
    fun organizerRequiresBothDocumentsAndReceivesOrganizerRole() {
        val register = validRegistration("organizador.nuevo@correo.com")
        register.onAccountTypeChange(true)
        register.onContinuarClick()
        assertEquals(2, register.uiState.value.currentStep)
        register.registrar()
        assertNotNull(register.uiState.value.documentError)
        assertNull(register.uiState.value.registeredUser)
        register.onDocumentFrontSelected("content://test/front")
        register.onDocumentBackSelected("content://test/back")
        register.registrar()
        assertEquals(UserRole.ORGANIZER, register.uiState.value.registeredUser?.role)
    }

    @Test
    fun homeRejectsInvalidMandatoryFieldsAndDoesNotInventUserData() {
        val user = FakeUsers.users.first()
        val cases = listOf(
            user.copy(id = "") to "id",
            user.copy(name = "") to "name",
            user.copy(email = "inválido") to "email",
            user.copy(registrationDate = Date(0)) to "registrationDate",
            user.copy(registrationDate = Date(System.currentTimeMillis() + 60_000)) to "registrationDate",
            user.copy(phoneNumber = "abc") to "phoneNumber"
        )
        cases.forEach { (invalid, field) ->
            FakeUsers.users.clear()
            FakeUsers.users.add(invalid)
            val home = HomeViewModel()
            home.loadUser(invalid.id)
            assertTrue(home.uiState.value.loadResult is RequestResult.Failure)
            assertTrue(home.uiState.value.userErrors.containsKey(field))
            assertNull(home.uiState.value.user)
            assertEquals("", home.uiState.value.userName)
        }
    }

    @Test
    fun homeAcceptsOptionalNullFieldsAndRejectsMissingUser() {
        val user = FakeUsers.users.first().copy(profilePictureUrl = null, phoneNumber = null)
        FakeUsers.users[0] = user
        val home = HomeViewModel()
        home.loadUser(user.id)
        assertEquals(RequestResult.Success, home.uiState.value.loadResult)
        assertEquals(user.name, home.uiState.value.userName)
        home.loadUser("no-existe")
        assertTrue(home.uiState.value.loadResult is RequestResult.Failure)
        assertNull(home.uiState.value.user)
    }

    @Test
    fun closingSessionClearsObservableAndRestorableUserId() {
        val handle = SavedStateHandle()
        val session = SessionViewModel(handle)
        session.signIn(FakeUsers.users.first())
        assertEquals(FakeUsers.users.first().id, SessionViewModel(handle).uiState.value.userId)
        session.signOut()
        assertNull(session.uiState.value.userId)
        assertNull(SessionViewModel(handle).uiState.value.userId)
    }

    private fun validRegistration(email: String) = RegistroViewModel().apply {
        onNombreChange(" Nueva Persona ")
        onEmailChange(email)
        onTelefonoChange("+57 310 555 1212")
        onPasswordChange("ClaveNueva123")
        onConfirmPasswordChange("ClaveNueva123")
        onTerminosChange(true)
    }
}
