package com.eventoscomunitarios.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eventoscomunitarios.app.core.util.AppLanguage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class LanguageSettingsTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private var previousLanguage = ""

    @Before fun useSpanish() {
        previousLanguage = AppLanguage.languageTag(compose.activity)
        compose.activityRule.scenario.onActivity { AppLanguage.apply(it, "es") }
        waitForText("Comenzar")
    }

    @After fun restoreLanguage() {
        compose.activityRule.scenario.onActivity { AppLanguage.apply(it, previousLanguage) }
        compose.waitForIdle()
    }

    @Test fun profileLanguageAppliesImmediatelyAndSurvivesRecreation() {
        compose.onNodeWithText("Comenzar").performClick()
        waitForText("Bienvenido de vuelta")
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("ana.torres@correo.com")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("12345678")
        compose.onNodeWithText("Iniciar sesión").performScrollTo().performClick()
        waitForText("Hola, Ana María Torres")
        compose.onNodeWithText("Perfil").performClick()
        waitForText("Mi perfil")

        compose.onNodeWithTag("profile_language_en").performScrollTo().performClick()
        waitForText("My profile")
        compose.onNodeWithText("App language").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("profile_language_en").assertIsSelected()
        compose.onNodeWithText("Sign out").performScrollTo().assertIsDisplayed()

        compose.activityRule.scenario.recreate()
        waitForText("My profile")
        compose.onNodeWithTag("profile_language_en").performScrollTo().assertIsSelected()

        compose.onNodeWithTag("profile_language_es").performScrollTo().performClick()
        waitForText("Mi perfil")
        compose.onNodeWithText("Idioma de la aplicación").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("profile_language_es").assertIsSelected()
    }

    private fun waitForText(text: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }
}
