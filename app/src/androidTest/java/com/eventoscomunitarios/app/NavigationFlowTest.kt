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
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eventoscomunitarios.app.core.data.InMemoryUserRepository
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltAndroidRule

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class NavigationFlowTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    private val users = InMemoryUserRepository().users.value
    private fun password(userId: String) = when(userId) {
        "participant-ana" -> "12345678"
        "administrator-laura" -> "Admin1234"
        else -> "Organizador123"
    }

    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun loginProfileRotationAndLogoutClearProtectedBackStack() {
        openLogin()
        val user = users.first()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput(user.email)
        compose.onAllNodes(hasSetTextAction())[1].performTextInput(password(user.id))
        compose.onAllNodes(hasSetTextAction())[1].performImeAction()
        compose.onNodeWithText("Iniciar sesión").performScrollTo().assertIsEnabled().performClick()
        waitForScreen("Hola, ${user.name}")
        compose.onNodeWithText("Perfil").performClick()
        waitForScreen("Mi perfil")
        compose.onNodeWithText(user.email).assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        waitForScreen("Mi perfil")
        compose.onNodeWithText(user.email).assertIsDisplayed()
        compose.onNodeWithText("Cerrar sesión").performScrollTo().performClick()
        waitForScreen("Bienvenido de vuelta")
        compose.onNodeWithText("Hola, ${user.name}").assertDoesNotExist()
        compose.onNodeWithText("Mi perfil").assertDoesNotExist()
        compose.onAllNodes(hasSetTextAction())[0].assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        // Android 12+ envía la actividad raíz al fondo; versiones anteriores pueden destruirla.
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (compose.activityRule.scenario.state.isAtLeast(Lifecycle.State.STARTED) &&
            SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(50)
        }
        assertFalse(compose.activityRule.scenario.state.isAtLeast(Lifecycle.State.STARTED))
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
    fun participantSeesOnlyOwnEnrollmentsAndCanPublish() {
        val participant = users.first { it.id == "participant-ana" }
        loginAs(participant.id)
        compose.onNodeWithTag("create_event_button").assertIsDisplayed()

        compose.onNodeWithText("Mis eventos").performClick()
        waitForScreen("Mis eventos")
        compose.onNodeWithText("Torneo de microfútbol").assertIsDisplayed()
        compose.onNodeWithText("Club de lectura barrial").assertIsDisplayed()

        compose.onNodeWithText("Creados").performClick()
        compose.onNodeWithText("Aún no has creado ningún evento.").assertIsDisplayed()
        compose.onNodeWithText("Editar").assertDoesNotExist()
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
        compose.onAllNodesWithText("Editar")[0].assertIsDisplayed().performClick()
        waitForScreen("Editar evento")
        compose.onNodeWithText("Torneo de microfútbol").assertIsDisplayed()
    }

    @Test
    fun participantCanOpenMapAndAdministratorGetsAdminNavigation() {
        loginAs("participant-ana")
        compose.onNodeWithText("Mapa").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("event_map").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("event_map").assertIsDisplayed()
        compose.onNodeWithTag("map_nav_home").performClick()
        waitForScreen("Hola, Ana María Torres")

        compose.onNodeWithText("Perfil").performClick()
        waitForScreen("Mi perfil")
        compose.onNodeWithText("Cerrar sesión").performScrollTo().performClick()
        waitForScreen("Bienvenido de vuelta")

        val admin = users.first { it.id == "administrator-laura" }
        compose.onAllNodes(hasSetTextAction())[0].performTextInput(admin.email)
        compose.onAllNodes(hasSetTextAction())[1].performTextInput(password(admin.id))
        compose.onNodeWithText("Iniciar sesión").performScrollTo().assertIsEnabled().performClick()
        waitForScreen("Panel de control")
        compose.onNodeWithTag("admin_nav_moderation").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("admin_moderation_screen").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("admin_moderation_screen").assertIsDisplayed()
        compose.onNodeWithTag("admin_nav_users").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("admin_users_screen").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("admin_users_screen").assertIsDisplayed()
        compose.onNodeWithTag("admin_nav_profile").performClick()
        waitForScreen("Mi perfil")
    }

    private fun openLogin() {
        compose.onNodeWithText("Comenzar").performClick()
        waitForScreen("Bienvenido de vuelta")
    }

    private fun loginAs(userId: String) {
        openLogin()
        val user = users.first { it.id == userId }
        compose.onAllNodes(hasSetTextAction())[0].performTextInput(user.email)
        compose.onAllNodes(hasSetTextAction())[1].performTextInput(password(user.id))
        compose.onAllNodes(hasSetTextAction())[1].performImeAction()
        compose.onNodeWithText("Iniciar sesión").performScrollTo().assertIsEnabled().performClick()
        waitForScreen("Hola, ${user.name}")
    }

    private fun waitForScreen(text: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithTag("loading_indicator").fetchSemanticsNodes().isEmpty()
        }
        compose.onAllNodesWithText(text)[0].assertIsDisplayed()
    }
}
