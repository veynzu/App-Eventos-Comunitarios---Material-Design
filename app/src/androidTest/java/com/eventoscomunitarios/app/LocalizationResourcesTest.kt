package com.eventoscomunitarios.app

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eventoscomunitarios.app.core.util.localizedMessage
import com.eventoscomunitarios.app.core.util.localizedNotificationMessage
import com.eventoscomunitarios.app.domain.model.AppNotification
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalizationResourcesTest {
    private fun context(language: String) = InstrumentationRegistry.getInstrumentation().targetContext.let { base ->
        base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) })
    }

    @Test fun fixedValidationAndFirebaseErrorsUseTheSelectedLanguage() {
        assertEquals("Enter a valid email address", context("en").localizedMessage("Ingresa un correo válido"))
        assertEquals("Correo o contraseña incorrectos.", context("es").localizedMessage("Correo o contraseña incorrectos."))
        assertEquals("Incorrect email or password.", context("en").localizedMessage("Correo o contraseña incorrectos."))
    }

    @Test fun notificationTemplatesTranslateWithoutChangingAuthorNamesOrEventTitles() {
        val notice = AppNotification(1, "user", "Nuevo comentario", "Ana María comentó en Festival de música.", 5, 0)
        assertEquals("Ana María commented on Festival de música.", context("en").localizedNotificationMessage(notice))
        val moderatorNotice = notice.copy(title = "Publicación por revisar", message = "Inicio")
        assertEquals("Inicio", context("en").localizedNotificationMessage(moderatorNotice))
    }
}
