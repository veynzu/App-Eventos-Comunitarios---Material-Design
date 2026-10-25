package com.eventoscomunitarios.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eventoscomunitarios.app.features.auth.FakeUsers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun loginProfileRotationAndLogoutClearProtectedBackStack() {
        openLogin()
        val user = FakeUsers.users.first()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput(user.email)
        compose.onAllNodes(hasSetTextAction())[1].performTextInput(FakeUsers.passwords.getValue(user.id))
        compose.onAllNodes(hasSetTextAction())[1].performImeAction()
        compose.onNodeWithText("Iniciar sesión").performScrollTo().assertIsEnabled().performClick()
        waitForScreen("Hola, ${user.name}")
        compose.onNodeWithText("Perfil").performClick()
        waitForScreen("Mi perfil")
        compose.onNodeWithText(user.email).assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        waitForScreen("Mi perfil")
        compose.onNodeWithText(user.email).assertIsDisplayed()
        compose.onNodeWithText("Cerrar sesión").performClick()
        waitForScreen("Bienvenido de vuelta")
        compose.onNodeWithText("Hola, ${user.name}").assertDoesNotExist()
        compose.onNodeWithText("Mi perfil").assertDoesNotExist()
        compose.onAllNodes(hasSetTextAction())[0].assertIsDisplayed()
        pressBackUnconditionally()
        compose.onNodeWithText("Hola, ${user.name}").assertDoesNotExist()
        compose.onNodeWithText("Mi perfil").assertDoesNotExist()
    }

    @Test
    fun spinnerAppearsDuringNavigationAndBackReturnsToLogin() {
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Comenzar").performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("loading_indicator").assertIsDisplayed()
        compose.mainClock.autoAdvance = true
        waitForScreen("Bienvenido de vuelta")
        compose.onNodeWithTag("loading_indicator").assertDoesNotExist()
        compose.onNodeWithText("Crear cuenta").performClick()
        waitForScreen("Crear cuenta")
        pressBack()
        waitForScreen("Bienvenido de vuelta")
        compose.onNodeWithText("¿Olvidaste tu contraseña?").performClick()
        waitForScreen("Recuperar contraseña")
        pressBack()
        waitForScreen("Bienvenido de vuelta")
    }

    @Test
    fun participantSeesOnlyEnrolledEventsAndCannotManageCreatedEvents() {
        val participant = FakeUsers.users.first { it.id == "participant-ana" }
        loginAs(participant.id)
        compose.onNodeWithTag("create_event_button").assertDoesNotExist()

        compose.onNodeWithText("Mis eventos").performClick()
        waitForScreen("Mis eventos")
        compose.onNodeWithText("Torneo de microfútbol").assertIsDisplayed()
        compose.onNodeWithText("Club de lectura barrial").assertIsDisplayed()

        compose.onNodeWithText("Creados").performClick()
        compose.onNodeWithText("Aún no has creado ningún evento.").assertIsDisplayed()
        compose.onNodeWithText("Administrar").assertDoesNotExist()
    }

    @Test
    fun organizerCanOpenCreateAndEditOnlyOwnedEvents() {
        loginAs("organizer-carlos")

        compose.onNodeWithTag("create_event_button").assertIsDisplayed().performClick()
        waitForScreen("Crear evento")
        pressBack()
        waitForScreen("Hola, Carlos Gómez")

        compose.onNodeWithText("Mis eventos").performClick()
        waitForScreen("Mis eventos")
        compose.onNodeWithText("No estás inscrito en ningún evento.").assertIsDisplayed()
        compose.onNodeWithText("Creados").performClick()
        compose.onAllNodesWithText("Administrar")[0].assertIsDisplayed().performClick()
        waitForScreen("Editar evento")
        compose.onNodeWithText("Torneo de microfútbol").assertIsDisplayed()
    }

    private fun openLogin() {
        compose.onNodeWithText("Comenzar").performClick()
        waitForScreen("Bienvenido de vuelta")
    }

    private fun loginAs(userId: String) {
        openLogin()
        val user = FakeUsers.users.first { it.id == userId }
        compose.onAllNodes(hasSetTextAction())[0].performTextInput(user.email)
        compose.onAllNodes(hasSetTextAction())[1].performTextInput(FakeUsers.passwords.getValue(user.id))
        compose.onAllNodes(hasSetTextAction())[1].performImeAction()
        compose.onNodeWithText("Iniciar sesión").performScrollTo().assertIsEnabled().performClick()
        waitForScreen("Hola, ${user.name}")
    }

    private fun waitForScreen(text: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithTag("loading_indicator").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText(text).assertIsDisplayed()
    }
}
