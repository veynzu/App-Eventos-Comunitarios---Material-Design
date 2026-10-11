package com.eventoscomunitarios.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.eventoscomunitarios.app.navigation.AppNavigation
import com.eventoscomunitarios.app.ui.theme.AppeventoscomunitariosTheme

class MainActivity : ComponentActivity() {
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
