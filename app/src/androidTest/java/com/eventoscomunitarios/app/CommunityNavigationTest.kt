package com.eventoscomunitarios.app

import android.graphics.Bitmap
import javax.inject.Inject
import androidx.test.platform.app.InstrumentationRegistry
import com.eventoscomunitarios.app.domain.repository.EventRepository
import com.eventoscomunitarios.app.core.data.InMemoryEventRepository
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltAndroidRule

/** Flujos reales de Compose; las pruebas de reglas de negocio viven en SocialViewModelTest. */
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class CommunityNavigationTest {
    @Inject lateinit var eventRepository: EventRepository
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun participantNavigatesNotificationsAndCommentsAndVotesOnEvent() {
        loginParticipant()
        compose.onNodeWithTag("home_notifications").assertIsDisplayed().performClick()
        waitForScreen("Notificaciones")
        capture("community-notifications")
        compose.onNodeWithContentDescription("Volver").performClick()
        waitForScreen("Hola, Ana María Torres")

        compose.onNodeWithTag("home_feed")
            .performScrollToNode(hasText("Torneo de microfútbol"))
        compose.onNodeWithText("Torneo de microfútbol").performClick()
        waitForScreen("Detalle del evento")
        compose.onNodeWithText("Torneo de microfútbol").assertIsDisplayed()
        capture("community-event-detail")

        compose.onNode(lazyList).performScrollToNode(hasText("Valoración de la comunidad"))
        compose.onNodeWithText("0 positivos").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("1 positivos").assertIsSelected()
        compose.onNodeWithText("1 positivos").performClick()
        compose.onNodeWithText("0 positivos").assertIsDisplayed()

        hilt.inject()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            runBlocking {
                val repository = eventRepository as InMemoryEventRepository
                repository.save(repository.events.value.first { it.id == 1 }.copy(
                    startsAt = System.currentTimeMillis() - 7200000, endsAt = System.currentTimeMillis() - 1000, isFinished = true))
            }
        }
        compose.onNode(lazyList).performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput("Prueba de navegación comunitaria")
        closeSoftKeyboard()
        compose.onNodeWithText("Publicar comentario").performScrollTo().assertIsEnabled().performClick()
        compose.onNode(lazyList).performScrollToNode(hasText("Prueba de navegación comunitaria"))
        compose.onNodeWithText("Prueba de navegación comunitaria").assertIsDisplayed()
        capture("community-event-comments")
        compose.onNodeWithContentDescription("Atrás").performClick()
        waitForScreen("Perfil")
    }

    @Test fun profileCanCancelEditsAndDeletionAndOpenNotifications() {
        loginParticipant()
        compose.onNodeWithText("Perfil").performClick()
        waitForScreen("Mi perfil")
        compose.onNodeWithText("ana.torres@correo.com").assertIsDisplayed()
        capture("community-profile")

        compose.onNodeWithText("Editar perfil").performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("Cambio sin guardar")
        closeSoftKeyboard()
        compose.onNodeWithText("Cancelar").performScrollTo().performClick()
        compose.onNodeWithText("Ana María Torres").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cambio sin guardar").assertDoesNotExist()

        compose.onNodeWithText("Eliminar mi cuenta").performScrollTo().performClick()
        compose.onNodeWithText("Conservar cuenta").assertIsDisplayed().performClick()
        compose.onNodeWithText("Mi perfil").assertIsDisplayed()
        compose.onNodeWithText("Editar perfil").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Estadísticas y reputación").performScrollTo().assertIsDisplayed()
        capture("community-profile-statistics")

        compose.onNodeWithContentDescription("Notificaciones").performClick()
        waitForScreen("Notificaciones")
        compose.onNodeWithContentDescription("Volver").performClick()
        waitForScreen("Mi perfil")
        compose.onNodeWithText("ana.torres@correo.com").performScrollTo().assertIsDisplayed()
    }

    private fun loginParticipant() {
        compose.onNodeWithText("Comenzar").performClick()
        waitForScreen("Bienvenido de vuelta")
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("ana.torres@correo.com")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("12345678")
        compose.onAllNodes(hasSetTextAction())[1].performImeAction()
        compose.onNodeWithText("Iniciar sesión").performScrollTo().assertIsEnabled().performClick()
        waitForScreen("Hola, Ana María Torres")
    }

    private val lazyList = SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)

    private fun waitForScreen(text: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithTag("loading_indicator").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val directory = requireNotNull(compose.activity.getExternalFilesDir("screenshots"))
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}
