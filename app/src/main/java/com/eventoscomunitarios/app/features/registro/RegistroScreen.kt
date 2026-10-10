package com.eventoscomunitarios.app.features.registro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
fun RegistroScreen(
    viewModel: RegistroViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.registroResult) {
        when (val result = state.registroResult) {
            RequestResult.Success -> {
                snackbarHostState.showSnackbar("Registro exitoso")
                viewModel.clearRegistroResult()
            }

            is RequestResult.Failure -> {
                snackbarHostState.showSnackbar(result.message)
                viewModel.clearRegistroResult()
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
            RegistroContent(
                state = state,
                onNombreChange = viewModel::onNombreChange,
                onEmailChange = viewModel::onEmailChange,
                onTelefonoChange = viewModel::onTelefonoChange,
                onPasswordChange = viewModel::onPasswordChange,
                onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
                onAccountTypeChange = viewModel::onAccountTypeChange,
                onTerminosChange = viewModel::onTerminosChange,
                onRegistrar = viewModel::registrar,
                onNavigateBack = onNavigateBack
            )
        }
    }
}

@Composable
private fun RegistroContent(
    state: RegistroUiState,
    onNombreChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onTelefonoChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onAccountTypeChange: (Boolean) -> Unit,
    onTerminosChange: (Boolean) -> Unit,
    onRegistrar: () -> Unit,
    onNavigateBack: () -> Unit
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
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 8.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Volver",
                        tint = TextoPrincipal
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Crear cuenta",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextoPrincipal
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(azulPrincipal)
                        .align(Alignment.CenterStart)
                )
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(2.dp)
                        .background(azulPrincipal)
                        .align(Alignment.CenterStart)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Paso 1 de 1 - Datos personales",
                    fontSize = 12.sp,
                    color = azulPrincipal,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = state.nombre,
                    onValueChange = onNombreChange,
                    label = {
                        Text("Nombre completo")
                    },
                    leadingIcon = {
                        IconoUsuario()
                    },
                    isError = state.nombreError != null,
                    supportingText = {
                        state.nombreError?.let { error ->
                            Text(error)
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
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

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = {
                        Text("Correo electrónico")
                    },
                    leadingIcon = {
                        IconoCorreoReg()
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

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.telefono,
                    onValueChange = onTelefonoChange,
                    label = {
                        Text("Teléfono")
                    },
                    leadingIcon = {
                        IconoTelefono()
                    },
                    isError = state.telefonoError != null,
                    supportingText = {
                        state.telefonoError?.let { error ->
                            Text(error)
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
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

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = {
                        Text("Contraseña")
                    },
                    leadingIcon = {
                        IconoCandadoReg()
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                mostrarContrasena = !mostrarContrasena
                            }
                        ) {
                            IconoOjoReg(visible = mostrarContrasena)
                        }
                    },
                    visualTransformation = if (mostrarContrasena) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    isError = state.passwordError != null,
                    supportingText = {
                        if (state.passwordError != null) {
                            Text(state.passwordError)
                        } else {
                            Text("8+ caracteres, 1 mayúscula y 1 número")
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
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

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.confirmPassword,
                    onValueChange = onConfirmPasswordChange,
                    label = {
                        Text("Confirmar contraseña")
                    },
                    leadingIcon = {
                        IconoCandadoReg()
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    isError = state.confirmPasswordError != null,
                    supportingText = {
                        state.confirmPasswordError?.let { error ->
                            Text(error)
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
                        cursorColor = azulPrincipal,
                        errorBorderColor = Color(0xFFD32F2F),
                        errorLabelColor = Color(0xFFD32F2F),
                        errorSupportingTextColor = Color(0xFFD32F2F)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Tipo de cuenta",
                    fontSize = 14.sp,
                    color = TextoSecundario,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clickable { onAccountTypeChange(false) },
                        shape = RoundedCornerShape(
                            topStart = 50f,
                            bottomStart = 50f
                        ),
                        color = if (!state.isOrganizer) AzulClaro else Fondo,
                        border = BorderStroke(
                            1.dp,
                            if (!state.isOrganizer) azulPrincipal else Gris
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Cliente",
                                fontSize = 14.sp,
                                color = if (!state.isOrganizer) azulPrincipal else TextoSecundario
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clickable { onAccountTypeChange(true) },
                        shape = RoundedCornerShape(
                            topEnd = 50f,
                            bottomEnd = 50f
                        ),
                        color = if (state.isOrganizer) AzulClaro else Fondo,
                        border = BorderStroke(
                            1.dp,
                            if (state.isOrganizer) azulPrincipal else Gris
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Organizador",
                                fontSize = 14.sp,
                                color = if (state.isOrganizer) azulPrincipal else TextoSecundario
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = state.terminos,
                        onCheckedChange = onTerminosChange,
                        modifier = Modifier.size(32.dp),
                        colors = CheckboxDefaults.colors(
                            checkedColor = azulPrincipal
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Acepto los Términos y la Política de datos",
                        fontSize = 12.sp,
                        color = TextoSecundario
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onRegistrar,
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
                        text = "Continuar",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun IconoUsuario() {
    Canvas(modifier = Modifier.size(20.dp)) {
        val color = Gris
        val grosor = 1.5.dp.toPx()

        drawCircle(
            color = color,
            radius = size.width * 0.2f,
            center = Offset(size.width * 0.5f, size.height * 0.35f),
            style = Stroke(width = grosor)
        )

        val bodyPath = Path().apply {
            moveTo(size.width * 0.2f, size.height * 0.85f)
            quadraticTo(
                size.width * 0.2f,
                size.height * 0.65f,
                size.width * 0.5f,
                size.height * 0.65f
            )
            quadraticTo(
                size.width * 0.8f,
                size.height * 0.65f,
                size.width * 0.8f,
                size.height * 0.85f
            )
        }
        drawPath(
            path = bodyPath,
            color = color,
            style = Stroke(width = grosor)
        )
    }
}

@Composable
private fun IconoTelefono() {
    Canvas(modifier = Modifier.size(20.dp)) {
        val color = Gris
        val grosor = 1.5.dp.toPx()

        val phonePath = Path().apply {
            moveTo(
                size.width * 0.3f,
                size.height * 0.2f
            )
            quadraticTo(
                size.width * 0.3f,
                size.height * 0.1f,
                size.width * 0.5f,
                size.height * 0.1f
            )
            quadraticTo(
                size.width * 0.7f,
                size.height * 0.1f,
                size.width * 0.7f,
                size.height * 0.2f
            )
            lineTo(
                size.width * 0.7f,
                size.height * 0.5f
            )
            quadraticTo(
                size.width * 0.7f,
                size.height * 0.6f,
                size.width * 0.5f,
                size.height * 0.6f
            )
            quadraticTo(
                size.width * 0.3f,
                size.height * 0.6f,
                size.width * 0.3f,
                size.height * 0.5f
            )
            close()
        }
        drawPath(
            path = phonePath,
            color = color,
            style = Stroke(width = grosor)
        )

        drawCircle(
            color = color,
            radius = 1.dp.toPx(),
            center = Offset(size.width * 0.5f, size.height * 0.5f)
        )
    }
}

@Composable
private fun IconoCorreoReg() {
    Canvas(modifier = Modifier.size(20.dp)) {
        val color = Gris
        val grosor = 1.5.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.12f, size.height * 0.22f),
            size = Size(size.width * 0.76f, size.height * 0.56f),
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = Stroke(width = grosor)
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.14f, size.height * 0.26f),
            end = Offset(size.width * 0.50f, size.height * 0.53f),
            strokeWidth = grosor
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.50f, size.height * 0.53f),
            end = Offset(size.width * 0.86f, size.height * 0.26f),
            strokeWidth = grosor
        )
    }
}

@Composable
private fun IconoCandadoReg() {
    Canvas(modifier = Modifier.size(20.dp)) {
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
            cornerRadius = CornerRadius(2.dp.toPx()),
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
private fun IconoOjoReg(visible: Boolean) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val color = Gris
        val grosor = 1.5.dp.toPx()
        val ojo = Path().apply {
            moveTo(
                size.width * 0.08f,
                size.height * 0.50f
            )
            quadraticTo(
                size.width * 0.50f,
                size.height * 0.05f,
                size.width * 0.92f,
                size.height * 0.50f
            )
            quadraticTo(
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

@Preview(
    showBackground = true,
    showSystemUi = true,
    name = "Vista previa del registro"
)
@Composable
private fun RegistroScreenPreview() {
    AppeventoscomunitariosTheme(
        darkTheme = false,
        dynamicColor = false
    ) {
        RegistroScreen(
            viewModel = remember { RegistroViewModel() },
            onNavigateBack = {}
        )
    }
}