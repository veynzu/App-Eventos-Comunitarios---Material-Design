package com.eventoscomunitarios.app

import android.app.Application
import android.content.Context
import com.eventoscomunitarios.app.core.util.AppLanguage
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

class ShareYouTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)

    override fun onStart() {
        // Las aserciones existentes usan español; las pruebas de idioma lo cambian explícitamente.
        AppLanguage.apply(targetContext, "es")
        super.onStart()
    }
}
