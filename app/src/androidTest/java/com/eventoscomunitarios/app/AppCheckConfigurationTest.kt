package com.eventoscomunitarios.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eventoscomunitarios.app.core.util.initializeAppCheck
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in: comprueba exclusivamente App Check real; no crea publicaciones ni cuentas. */
@RunWith(AndroidJUnit4::class)
class AppCheckConfigurationTest {
    @Test fun registeredDebugDeviceReceivesAValidAppCheckToken() = runBlocking {
        assumeTrue("Requiere el registro previo del dispositivo de prueba en App Check",
            InstrumentationRegistry.getArguments().getString("appCheckSmoke") == "true")
        initializeAppCheck()
        val result = FirebaseAppCheck.getInstance().getAppCheckToken(false).await()
        assertTrue(result.token.isNotBlank())
        assertTrue(result.expireTimeMillis > System.currentTimeMillis())
    }
}
