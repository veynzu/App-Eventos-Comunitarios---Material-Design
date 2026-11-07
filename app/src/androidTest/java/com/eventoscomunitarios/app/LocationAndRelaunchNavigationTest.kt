package com.eventoscomunitarios.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eventoscomunitarios.app.core.data.InMemoryEventRepository
import com.eventoscomunitarios.app.domain.repository.EventRepository
import com.eventoscomunitarios.app.domain.usecase.LocationUseCases
import com.eventoscomunitarios.app.domain.model.Coordinates
import androidx.compose.ui.geometry.Offset
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class LocationAndRelaunchNavigationTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @Inject lateinit var eventRepository: EventRepository
    @Inject lateinit var locations: LocationUseCases

    @Test fun choosingASearchPointUpdatesHomeAndMap() {
        hilt.inject()
        login("ana.torres@correo.com", "12345678")
        compose.onNodeWithText("Buscar en otra zona").performScrollTo().performClick()
        waitForTag("location_picker")
        compose.onNodeWithTag("location_picker_map").performTouchInput { click(Offset(center.x + 100f, center.y)) }
        compose.waitUntil(10000) { compose.onAllNodes(hasText("Armenia, Quindío") and hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("location_address").performTextReplacement("Zona elegida para buscar")
        closeSoftKeyboard()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("confirm_location").fetchSemanticsNodes().none { it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) } }
        capture("location-picker", "location_picker")
        compose.onNodeWithTag("confirm_location").assertIsEnabled().performClick()
        assertTrue(locations.search.value.coordinates != Coordinates.ARMENIA)
        compose.onNodeWithText("Zona elegida para buscar").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Mapa").performClick()
        waitForTag("map_address")
        compose.onNodeWithText("Zona elegida para buscar").assertIsDisplayed()
        capture("map-search-area")
    }

    @Test fun creatorCanOpenANewEditionWithOriginalData() {
        hilt.inject()
        val repository = eventRepository as InMemoryEventRepository
        val original = repository.events.value.first { it.id == 1 }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { runBlocking {
            repository.save(original.copy(startsAt = System.currentTimeMillis() - 7200000,
                endsAt = System.currentTimeMillis() - 1000, isFinished = true))
        } }
        login("carlos.gomez@correo.com", "Organizador123")
        compose.onNodeWithText("Mis eventos").performClick()
        waitForTag("my_events_screen")
        compose.onNodeWithText("Creados").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(original.title))
        compose.onNodeWithTag("relaunch_event_1").performScrollTo().performClick()
        waitForTag("create_event_screen")
        compose.onNodeWithText("Nueva edición del evento").assertIsDisplayed()
        compose.onNodeWithText(original.title).assertIsDisplayed()
        capture("relaunch-form")
        assertTrue(repository.events.value.first { it.id == original.id }.isFinished)
    }

    private fun login(email: String, password: String) {
        compose.onNodeWithText("Comenzar").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Bienvenido de vuelta").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasSetTextAction())[0].performTextInput(email)
        compose.onAllNodes(hasSetTextAction())[1].performTextInput(password)
        closeSoftKeyboard()
        compose.onNodeWithText("Iniciar sesión").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("home_feed").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForTag(tag: String) = compose.waitUntil(10000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun capture(name: String, tag: String? = null) {
        compose.waitForIdle()
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let { File(it) }
            ?: File(compose.activity.getExternalFilesDir(null), "screenshots")
        directory.mkdirs()
        val file = File(directory, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply {
            putString("additionalTestOutputFile_$name", file.absolutePath)
        })
    }
}
