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
        assertEquals("Your account is suspended. Contact the administrator.",
            context("en").localizedMessage("Tu cuenta está suspendida. Contacta al administrador."))
        assertEquals("Your account is blocked. Contact the administrator.",
            context("en").localizedMessage("Tu cuenta está bloqueada. Contacta al administrador."))
    }

    @Test fun notificationTemplatesTranslateWithoutChangingAuthorNamesOrEventTitles() {
        val notice = AppNotification(1, "user", "Nuevo comentario", "Ana María comentó en Festival de música.", 5, 0)
        assertEquals("Ana María commented on Festival de música.", context("en").localizedNotificationMessage(notice))
        val moderatorNotice = notice.copy(title = "Publicación por revisar", message = "Inicio")
        assertEquals("Inicio", context("en").localizedNotificationMessage(moderatorNotice))
    }

    @Test fun reactionNotificationsTranslateTitlesAndMessages() {
        val like = AppNotification(1, "user", "Nuevo like", "Ana María le dio like a Festival de música.", 5, 0)
        val dislike = like.copy(title = "Nuevo dislike", message = "Ana María le dio dislike a Festival de música.")
        assertEquals("New like", context("en").localizedMessage(like.title))
        assertEquals("New dislike", context("en").localizedMessage(dislike.title))
        assertEquals("Ana María liked Festival de música.", context("en").localizedNotificationMessage(like))
        assertEquals("Ana María disliked Festival de música.", context("en").localizedNotificationMessage(dislike))
        assertEquals(like.message, context("es").localizedNotificationMessage(like))
        assertEquals(dislike.message, context("es").localizedNotificationMessage(dislike))
    }
}
