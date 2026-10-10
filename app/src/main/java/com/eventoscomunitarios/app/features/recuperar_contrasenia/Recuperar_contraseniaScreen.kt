package com.eventoscomunitarios.app.features.recuperar_contrasenia

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventoscomunitarios.app.ui.theme.AppeventoscomunitariosTheme
import com.eventoscomunitarios.app.ui.theme.AzulClaro
import com.eventoscomunitarios.app.ui.theme.Fondo
import com.eventoscomunitarios.app.ui.theme.Gris
import com.eventoscomunitarios.app.ui.theme.TextoPrincipal
import com.eventoscomunitarios.app.ui.theme.TextoSecundario

@Composable
fun RecuperarContrasenaScreen(
    viewModel: RecuperarContrasenaViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.solicitudValidada) {
        if (state.solicitudValidada) {
            snackbarHostState.showSnackbar(
                "Correo válido. La solicitud está lista para conectar con el servicio de recuperación."
            )
            viewModel.clearSolicitudValidada()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        RecuperarContrasenaContent(
            state = state,
            onEmailChange = viewModel::onEmailChange,
            onEnviarInstrucciones = viewModel::solicitarRecuperacion,
            onNavigateBack = onNavigateBack,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
private fun RecuperarContrasenaContent(
    state: RecuperarContrasenaUiState,
    onEmailChange: (String) -> Unit,
    onEnviarInstrucciones: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val azulPrincipal = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Fondo
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = Color(0xFFE4EBEF)
                        )
                    ) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Volver al inicio de sesión",
                                tint = TextoPrincipal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                IlustracionRecuperacion(azulPrincipal = azulPrincipal)

                Spacer(modifier = Modifier.height(26.dp))

                Text(
                    text = "Recuperar contraseña",
                    fontSize = 23.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextoPrincipal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Ingresa el correo asociado a tu cuenta y te enviaremos instrucciones para restablecer tu contraseña.",
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                    color = TextoSecundario,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(34.dp))

                OutlinedTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = {
                        Text("Correo electrónico")
                    },
                    placeholder = {
                        Text("nombre@ejemplo.com")
                    },
                    leadingIcon = {
                        IconoCorreoRecuperacion()
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

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onEnviarInstrucciones,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(49.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = azulPrincipal,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Enviar instrucciones",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconoSeguridad()

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Si el correo existe en nuestra plataforma, recibirás los pasos para recuperar el acceso.",
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = TextoSecundario,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFFE8EEF1))
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "¿Recordaste tu contraseña?",
                    fontSize = 13.sp,
                    color = TextoSecundario,
                    textAlign = TextAlign.Center
                )

                TextButton(onClick = onNavigateBack) {
                    Text(
                        text = "Volver a iniciar sesión",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = azulPrincipal
                    )
                }
            }
        }
    }
}

@Composable
private fun IlustracionRecuperacion(azulPrincipal: Color) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .background(AzulClaro, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(42.dp)) {
            val color = Color(0xFF003B56)
            val grosor = 2.dp.toPx()

            drawRoundRect(
                color = color,
                topLeft = Offset(size.width * 0.08f, size.height * 0.22f),
                size = Size(size.width * 0.84f, size.height * 0.56f),
                cornerRadius = CornerRadius(4.dp.toPx()),
                style = Stroke(width = grosor)
            )

            drawLine(
                color = color,
                start = Offset(size.width * 0.10f, size.height * 0.27f),
                end = Offset(size.width * 0.50f, size.height * 0.56f),
                strokeWidth = grosor
            )

            drawLine(
                color = color,
                start = Offset(size.width * 0.50f, size.height * 0.56f),
                end = Offset(size.width * 0.90f, size.height * 0.27f),
                strokeWidth = grosor
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 2.dp, y = 2.dp)
                .size(24.dp)
                .background(azulPrincipal, CircleShape)
                .border(2.dp, AzulClaro, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(14.dp)) {
                val iconColor = Color.White
                val grosor = 1.4.dp.toPx()

                drawArc(
                    color = iconColor,
                    startAngle = 35f,
                    sweepAngle = 285f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.15f, size.height * 0.15f),
                    size = Size(size.width * 0.70f, size.height * 0.70f),
                    style = Stroke(width = grosor)
                )

                drawLine(
                    color = iconColor,
                    start = Offset(size.width * 0.12f, size.height * 0.20f),
                    end = Offset(size.width * 0.12f, size.height * 0.53f),
                    strokeWidth = grosor
                )

                drawLine(
                    color = iconColor,
                    start = Offset(size.width * 0.12f, size.height * 0.20f),
                    end = Offset(size.width * 0.43f, size.height * 0.20f),
                    strokeWidth = grosor
                )
            }
        }
    }
}

@Composable
private fun IconoCorreoRecuperacion() {
    Canvas(modifier = Modifier.size(20.dp)) {
        val color = Gris
        val grosor = 1.5.dp.toPx()

        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.10f, size.height * 0.20f),
            size = Size(size.width * 0.80f, size.height * 0.60f),
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = Stroke(width = grosor)
        )

        drawLine(
            color = color,
            start = Offset(size.width * 0.13f, size.height * 0.25f),
            end = Offset(size.width * 0.50f, size.height * 0.53f),
            strokeWidth = grosor
        )

        drawLine(
            color = color,
            start = Offset(size.width * 0.50f, size.height * 0.53f),
            end = Offset(size.width * 0.87f, size.height * 0.25f),
            strokeWidth = grosor
        )
    }
}

@Composable
private fun IconoSeguridad() {
    Canvas(modifier = Modifier.size(20.dp)) {
        val color = TextoSecundario
        val grosor = 1.4.dp.toPx()

        val escudo = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.50f, size.height * 0.05f)
            lineTo(size.width * 0.88f, size.height * 0.20f)
            lineTo(size.width * 0.88f, size.height * 0.48f)
            quadraticTo(
                size.width * 0.84f,
                size.height * 0.78f,
                size.width * 0.50f,
                size.height * 0.95f
            )
            quadraticTo(
                size.width * 0.16f,
                size.height * 0.78f,
                size.width * 0.12f,
                size.height * 0.48f
            )
            lineTo(size.width * 0.12f, size.height * 0.20f)
            close()
        }

        drawPath(
            path = escudo,
            color = color,
            style = Stroke(width = grosor)
        )

        drawLine(
            color = color,
            start = Offset(size.width * 0.31f, size.height * 0.49f),
            end = Offset(size.width * 0.45f, size.height * 0.63f),
            strokeWidth = grosor
        )

        drawLine(
            color = color,
            start = Offset(size.width * 0.45f, size.height * 0.63f),
            end = Offset(size.width * 0.70f, size.height * 0.36f),
            strokeWidth = grosor
        )
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    name = "Vista previa - Recuperar contraseña"
)
@Composable
private fun RecuperarContrasenaScreenPreview() {
    AppeventoscomunitariosTheme(
        darkTheme = false,
        dynamicColor = false
    ) {
        RecuperarContrasenaScreen(
            viewModel = remember { RecuperarContrasenaViewModel() },
            onNavigateBack = {}
        )
    }
}


