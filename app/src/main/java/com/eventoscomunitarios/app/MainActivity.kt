package com.eventoscomunitarios.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.eventoscomunitarios.app.features.login.LoginScreen
import com.eventoscomunitarios.app.features.onboarding.OnboardingScreen
import com.eventoscomunitarios.app.features.recuperar_contrasenia.RecuperarContrasenaScreen
import com.eventoscomunitarios.app.features.registro.RegistroScreen
import com.eventoscomunitarios.app.ui.theme.AppeventoscomunitariosTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            AppeventoscomunitariosTheme {
                var mostrarLogin by rememberSaveable {
                    mutableStateOf(false)
                }

                var mostrarRegistro by rememberSaveable {
                    mutableStateOf(false)
                }

                var mostrarRecuperarContrasena by rememberSaveable {
                    mutableStateOf(false)
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        when {
                            mostrarRegistro -> {
                                RegistroScreen(
                                    onNavigateBack = {
                                        mostrarRegistro = false
                                    }
                                )
                            }

                            mostrarRecuperarContrasena -> {
                                RecuperarContrasenaScreen(
                                    onNavigateBack = {
                                        mostrarRecuperarContrasena = false
                                    }
                                )
                            }

                            mostrarLogin -> {
                                LoginScreen(
                                    onIniciarSesion = {},
                                    onRecuperarContrasena = {
                                        mostrarRecuperarContrasena = true
                                    },
                                    onCrearCuenta = {
                                        mostrarRegistro = true
                                    }
                                )
                            }

                            else -> {
                                OnboardingScreen(
                                    onContinuar = {
                                        mostrarLogin = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
