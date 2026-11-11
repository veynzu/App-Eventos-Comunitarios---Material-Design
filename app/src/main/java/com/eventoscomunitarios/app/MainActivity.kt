package com.eventoscomunitarios.app

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.eventoscomunitarios.app.navigation.AppNavigation
import com.eventoscomunitarios.app.core.util.AppLanguage
import com.eventoscomunitarios.app.ui.theme.AppeventoscomunitariosTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppeventoscomunitariosTheme {
                AppNavigation()
            }
        }
    }
}
