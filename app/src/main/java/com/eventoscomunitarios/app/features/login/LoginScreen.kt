package com.eventoscomunitarios.app.features.login

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.ui.theme.AppeventoscomunitariosTheme
import com.eventoscomunitarios.app.ui.theme.AzulClaro
import com.eventoscomunitarios.app.ui.theme.Fondo
import com.eventoscomunitarios.app.ui.theme.Gris
import com.eventoscomunitarios.app.ui.theme.TextoPrincipal
import com.eventoscomunitarios.app.ui.theme.TextoSecundario

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = viewModel(),
    onIniciarSesion: () -> Unit,
    onRecuperarContrasena: () -> Unit = {},
    onCrearCuenta: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.loginResult) {
        when (val result = state.loginResult) {
            RequestResult.Success -> {
                snackbarHostState.showSnackbar("Inicio de sesión exitoso")
                viewModel.clearLoginResult()
                onIniciarSesion()
            }

            is RequestResult.Failure -> {
                snackbarHostState.showSnackbar(result.message)
                viewModel.clearLoginResult()
            }

            RequestResult.Loading,
            null -> Unit
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier.padding(innerPadding)
        ) {
            LoginContent(
                state = state,
                onEmailChange = viewModel::onEmailChange,
                onPasswordChange = viewModel::onPasswordChange,
                onRecordarChange = viewModel::onRecordarChange,
                onIniciarSesion = viewModel::login,
                onRecuperarContrasena = onRecuperarContrasena,
                onCrearCuenta = onCrearCuenta
            )
        }
    }
}

@Composable
private fun LoginContent(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRecordarChange: (Boolean) -> Unit,
    onIniciarSesion: () -> Unit,
    onRecuperarContrasena: () -> Unit,
    onCrearCuenta: () -> Unit
) {
    var mostrarContrasena by rememberSaveable {
        mutableStateOf(false)
    }

    val azulPrincipal = MaterialTheme.colorScheme.primary

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Fondo
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Spacer(modifier = Modifier.height(48.dp))

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(AzulClaro, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    IconoFiesta()
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Bienvenido de vuelta",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextoPrincipal,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Inicia sesión para descubrir eventos en tu zona",
                    fontSize = 13.sp,
                    color = TextoSecundario,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = {
                        Text("Correo electrónico")
                    },
                    placeholder = {
                        Text("example@example.com")
                    },
                    leadingIcon = {
                        IconoCorreo()
                    },
                    isError = state.emailError != null,
                    supportingText = {
                        state.emailError?.let { error ->
                            Text(error)
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(5.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = azulPrincipal,
                        unfocusedBorderColor = Gris,
                        focusedLabelColor = azulPrincipal,
                        cursorColor = azulPrincipal
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = {
                        Text("Password")
                    },
                    leadingIcon = {
                        IconoCandado()
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                mostrarContrasena = !mostrarContrasena
                            }
                        ) {
                            IconoOjo(
                                visible = mostrarContrasena
                            )
                        }
                    },
                    visualTransformation =
                        if (mostrarContrasena) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                    isError = state.passwordError != null,
                    supportingText = {
                        if (state.passwordError != null) {
                            Text(state.passwordError)
                        } else {
                            Text("Mínimo 8 caracteres")
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(5.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = azulPrincipal,
                        unfocusedBorderColor = Gris,
                        focusedLabelColor = azulPrincipal,
                        cursorColor = azulPrincipal
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = state.recordar,
                            onCheckedChange = onRecordarChange,
                            modifier = Modifier.size(32.dp),
                            colors = CheckboxDefaults.colors(
                                checkedColor = azulPrincipal
                            )
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = "Recordarme",
                            fontSize = 12.sp,
                            color = TextoSecundario
                        )
                    }

                    TextButton(
                        onClick = onRecuperarContrasena,
                        contentPadding = PaddingValues(
                            horizontal = 4.dp
                        )
                    ) {
                        Text(
                            text = "¿Olvidaste tu contraseña?",
                            fontSize = 12.sp,
                            color = azulPrincipal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onIniciarSesion,
                    enabled = state.isFormValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = azulPrincipal,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Iniciar sesión",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        bottom = 20.dp,
                        top = 8.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "¿No tienes cuenta?",
                    fontSize = 13.sp,
                    color = TextoSecundario,
                    textAlign = TextAlign.Center
                )

                TextButton(
                    onClick = onCrearCuenta
                ) {
                    Text(
                        text = "Crear cuenta",
                        fontSize = 14.sp,
                        color = azulPrincipal
                    )
                }
            }
        }
    }
}

@Composable
private fun IconoFiesta() {
    Canvas(
        modifier = Modifier.size(30.dp)
    ) {
        val color = Color(0xFF001E31)
        val grosor = 1.8.dp.toPx()

        val cono = Path().apply {
            moveTo(
                size.width * 0.18f,
                size.height * 0.84f
            )
            lineTo(
                size.width * 0.40f,
                size.height * 0.36f
            )
            lineTo(
                size.width * 0.64f,
                size.height * 0.61f
            )
            close()
        }

        drawPath(
            path = cono,
            color = color,
            style = Stroke(width = grosor)
        )

        drawLine(
            color = color,
            start = Offset(
                size.width * 0.33f,
                size.height * 0.52f
            ),
            end = Offset(
                size.width * 0.48f,
                size.height * 0.68f
            ),
            strokeWidth = grosor
        )

        drawLine(
            color = color,
            start = Offset(
                size.width * 0.55f,
                size.height * 0.20f
            ),
            end = Offset(
                size.width * 0.61f,
                size.height * 0.35f
            ),
            strokeWidth = grosor
        )

        drawLine(
            color = color,
            start = Offset(
                size.width * 0.72f,
                size.height * 0.40f
            ),
            end = Offset(
                size.width * 0.88f,
                size.height * 0.44f
            ),
            strokeWidth = grosor
        )

        drawLine(
            color = color,
            start = Offset(
                size.width * 0.76f,
                size.height * 0.14f
            ),
            end = Offset(
                size.width * 0.70f,
                size.height * 0.28f
            ),
            strokeWidth = grosor
        )

        drawCircle(
            color = color,
            radius = 1.4.dp.toPx(),
            center = Offset(
                size.width * 0.86f,
                size.height * 0.24f
            )
        )
    }
}

@Composable
private fun IconoCorreo() {
    Canvas(
        modifier = Modifier.size(20.dp)
    ) {
        val color = Gris
        val grosor = 1.5.dp.toPx()

        drawRoundRect(
            color = color,
            topLeft = Offset(
                size.width * 0.12f,
                size.height * 0.22f
            ),
            size = Size(
                size.width * 0.76f,
                size.height * 0.56f
            ),
            cornerRadius = CornerRadius(
                2.dp.toPx()
            ),
            style = Stroke(width = grosor)
        )

        drawLine(
            color = color,
            start = Offset(
                size.width * 0.14f,
                size.height * 0.26f
            ),
            end = Offset(
                size.width * 0.50f,
                size.height * 0.53f
            ),
            strokeWidth = grosor
        )

        drawLine(
            color = color,
            start = Offset(
                size.width * 0.50f,
                size.height * 0.53f
            ),
            end = Offset(
                size.width * 0.86f,
                size.height * 0.26f
            ),
            strokeWidth = grosor
        )
    }
}

@Composable
private fun IconoCandado() {
    Canvas(
        modifier = Modifier.size(20.dp)
    ) {
        val color = Gris
        val grosor = 1.5.dp.toPx()

        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(
                size.width * 0.30f,
                size.height * 0.12f
            ),
            size = Size(
                size.width * 0.40f,
                size.height * 0.60f
            ),
            style = Stroke(width = grosor)
        )

        drawRoundRect(
            color = color,
            topLeft = Offset(
                size.width * 0.19f,
                size.height * 0.43f
            ),
            size = Size(
                size.width * 0.62f,
                size.height * 0.43f
            ),
            cornerRadius = CornerRadius(
                2.dp.toPx()
            ),
            style = Stroke(width = grosor)
        )

        drawCircle(
            color = color,
            radius = 1.3.dp.toPx(),
            center = Offset(
                size.width * 0.50f,
                size.height * 0.63f
            )
        )
    }
}

@Composable
private fun IconoOjo(
    visible: Boolean
) {
    Canvas(
        modifier = Modifier.size(20.dp)
    ) {
        val color = Gris
        val grosor = 1.5.dp.toPx()

        val ojo = Path().apply {
            moveTo(
                size.width * 0.08f,
                size.height * 0.50f
            )

            quadraticBezierTo(
                size.width * 0.50f,
                size.height * 0.05f,
                size.width * 0.92f,
                size.height * 0.50f
            )

            quadraticBezierTo(
                size.width * 0.50f,
                size.height * 0.95f,
                size.width * 0.08f,
                size.height * 0.50f
            )
        }

        drawPath(
            path = ojo,
            color = color,
            style = Stroke(width = grosor)
        )

        drawCircle(
            color = color,
            radius = size.width * 0.12f,
            center = Offset(
                size.width * 0.50f,
                size.height * 0.50f
            ),
            style = Stroke(width = grosor)
        )

        if (!visible) {
            drawLine(
                color = color,
                start = Offset(
                    size.width * 0.12f,
                    size.height * 0.12f
                ),
                end = Offset(
                    size.width * 0.88f,
                    size.height * 0.88f
                ),
                strokeWidth = grosor
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    AppeventoscomunitariosTheme(
        darkTheme = false,
        dynamicColor = false
    ) {
        LoginScreen(
            viewModel = remember { LoginViewModel() },
            onIniciarSesion = {}
        )
    }
}
