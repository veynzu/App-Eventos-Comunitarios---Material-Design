package com.eventoscomunitarios.app.features.registro

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
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
import com.eventoscomunitarios.app.domain.model.AppUser
import com.eventoscomunitarios.app.ui.theme.AppeventoscomunitariosTheme
import com.eventoscomunitarios.app.ui.theme.AzulClaro
import com.eventoscomunitarios.app.ui.theme.Fondo
import com.eventoscomunitarios.app.ui.theme.Gris
import com.eventoscomunitarios.app.ui.theme.TextoPrincipal
import com.eventoscomunitarios.app.ui.theme.TextoSecundario

@Composable
fun RegistroScreen(
    viewModel: RegistroViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onRegistroExitoso: (AppUser) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val frontDocumentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        viewModel.onDocumentFrontSelected(uri?.toString())
    }

    val backDocumentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        viewModel.onDocumentBackSelected(uri?.toString())
    }

    LaunchedEffect(state.registroResult) {
        when (val result = state.registroResult) {
            RequestResult.Success -> {
                snackbarHostState.showSnackbar("Cuenta creada correctamente")
                val registeredUser = state.registeredUser
                viewModel.clearRegistroResult()
                registeredUser?.let(onRegistroExitoso)
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
        RegistroContent(
            state = state,
            onNavigateBack = {
                if (state.currentStep == 2) {
                    viewModel.onBackFromDocuments()
                } else {
                    onNavigateBack()
                }
            },
            onNombreChange = viewModel::onNombreChange,
            onEmailChange = viewModel::onEmailChange,
            onTelefonoChange = viewModel::onTelefonoChange,
            onPasswordChange = viewModel::onPasswordChange,
            onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
            onAccountTypeChange = viewModel::onAccountTypeChange,
            onTerminosChange = viewModel::onTerminosChange,
            onSelectFrontDocument = {
                frontDocumentPicker.launch("image/*")
            },
            onSelectBackDocument = {
                backDocumentPicker.launch("image/*")
            },
            onContinuar = viewModel::onContinuarClick,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
private fun RegistroContent(
    state: RegistroUiState,
    onNavigateBack: () -> Unit,
    onNombreChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onTelefonoChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onAccountTypeChange: (Boolean) -> Unit,
    onTerminosChange: (Boolean) -> Unit,
    onSelectFrontDocument: () -> Unit,
    onSelectBackDocument: () -> Unit,
    onContinuar: () -> Unit,
    modifier: Modifier = Modifier
) {
    var mostrarContrasena by rememberSaveable {
        mutableStateOf(false)
    }

    val azulPrincipal = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Fondo
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowBack,
                        contentDescription = "Volver",
                        tint = TextoPrincipal
                    )
                }

                Text(
                    text = "Crear cuenta",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextoPrincipal
                )
            }

            LinearProgressIndicator(
                progress = if (state.currentStep == 2) 1f else 0.5f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = azulPrincipal,
                trackColor = Color(0xFFE0E4E8)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (state.currentStep == 1 && state.isOrganizer) {
                    "Paso 1 de 2 · Datos personales"
                } else if (state.currentStep == 2) {
                    "Paso 2 de 2 · Verificación de identidad"
                } else {
                    "Datos personales"
                },
                fontSize = 11.sp,
                color = azulPrincipal,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.currentStep == 1) {
                    RegistroField(
                        value = state.nombre,
                        onValueChange = onNombreChange,
                        label = "Nombre completo",
                        placeholder = "Ana María Torres",
                        error = state.nombreError,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null
                            )
                        },
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    )

                    RegistroField(
                        value = state.email,
                        onValueChange = onEmailChange,
                        label = "Correo electrónico",
                        placeholder = "ana.torres@correo.com",
                        error = state.emailError,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Email,
                                contentDescription = null
                            )
                        },
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    )

                    RegistroField(
                        value = state.telefono,
                        onValueChange = onTelefonoChange,
                        label = "Teléfono",
                        placeholder = "+57 310 555 4821",
                        error = state.telefonoError,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Phone,
                                contentDescription = null
                            )
                        },
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    )

                    RegistroField(
                        value = state.password,
                        onValueChange = onPasswordChange,
                        label = "Contraseña",
                        placeholder = "",
                        error = state.passwordError,
                        supportingText = if (state.passwordError == null) {
                            "8+ caracteres, 1 mayúscula y 1 número"
                        } else {
                            null
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    mostrarContrasena = !mostrarContrasena
                                }
                            ) {
                                Icon(
                                    imageVector = if (mostrarContrasena) {
                                        Icons.Outlined.VisibilityOff
                                    } else {
                                        Icons.Outlined.Visibility
                                    },
                                    contentDescription = if (mostrarContrasena) {
                                        "Ocultar contraseña"
                                    } else {
                                        "Mostrar contraseña"
                                    }
                                )
                            }
                        },
                        visualTransformation = if (mostrarContrasena) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next
                    )

                    RegistroField(
                        value = state.confirmPassword,
                        onValueChange = onConfirmPasswordChange,
                        label = "Confirmar contraseña",
                        placeholder = "",
                        error = state.confirmPasswordError,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null
                            )
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    )

                    Text(
                        text = "Tipo de cuenta",
                        fontSize = 12.sp,
                        color = TextoPrincipal
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(50))
                            .border(
                                width = 1.dp,
                                color = Gris,
                                shape = RoundedCornerShape(50)
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RoleOption(
                            text = "Cliente",
                            selected = !state.isOrganizer,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onAccountTypeChange(false)
                            }
                        )

                        RoleOption(
                            text = "Organizador",
                            selected = state.isOrganizer,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onAccountTypeChange(true)
                            }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = state.terminos,
                            onCheckedChange = onTerminosChange,
                            colors = CheckboxDefaults.colors(
                                checkedColor = azulPrincipal
                            )
                        )

                        Text(
                            text = "Acepto los Términos y la Política de datos",
                            fontSize = 11.sp,
                            color = TextoSecundario
                        )
                    }
                } else {
                    Text(
                        text = "A continuación, toma una foto de tu documento de identidad por ambos lados. Debe verse completo y con buena calidad para validar tus datos.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = TextoSecundario
                    )

                    Text(
                        text = "1) Frente de tu documento",
                        fontSize = 12.sp,
                        color = TextoPrincipal
                    )

                    DocumentoSelector(
                        selected = state.documentFrontUri != null,
                        onClick = onSelectFrontDocument
                    )

                    Text(
                        text = "2) Atrás de tu documento",
                        fontSize = 12.sp,
                        color = TextoPrincipal
                    )

                    DocumentoSelector(
                        selected = state.documentBackUri != null,
                        onClick = onSelectBackDocument
                    )

                    state.documentError?.let { error ->
                        Text(
                            text = error,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Text(
                        text = "La documentación se simula localmente en esta versión. No se envía a un servidor.",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = TextoSecundario
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onContinuar,
                enabled = state.isFormValid &&
                        (state.currentStep == 1 || state.documentsAreSelected),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = azulPrincipal,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF9DBACB),
                    disabledContentColor = Color.White
                )
            ) {
                Text(
                    text = "Continuar",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RegistroField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    error: String?,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardType: KeyboardType,
    imeAction: ImeAction
) {
    val azulPrincipal = MaterialTheme.colorScheme.primary

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(label)
        },
        placeholder = {
            if (placeholder.isNotBlank()) {
                Text(placeholder)
            }
        },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        isError = error != null,
        supportingText = {
            when {
                error != null -> Text(error)
                supportingText != null -> Text(supportingText)
            }
        },
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
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
}

@Composable
private fun RoleOption(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val azulPrincipal = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier
            .height(36.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (selected) AzulClaro else Color.Transparent,
        contentColor = if (selected) azulPrincipal else TextoPrincipal
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun DocumentoSelector(
    selected: Boolean,
    onClick: () -> Unit
) {
    val azulPrincipal = MaterialTheme.colorScheme.primary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = Color.White,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) azulPrincipal else Gris
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (selected) {
                    Icons.Outlined.CheckCircle
                } else {
                    Icons.Outlined.CameraAlt
                },
                contentDescription = null,
                tint = if (selected) azulPrincipal else TextoSecundario,
                modifier = Modifier.size(25.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = if (selected) "Documento seleccionado" else "Toca para seleccionar una foto",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (selected) azulPrincipal else TextoPrincipal
                )
                Text(
                    text = if (selected) "Puedes volver a seleccionarlo" else "Frente o reverso de la cédula",
                    fontSize = 10.sp,
                    color = TextoSecundario
                )
            }
        }
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    name = "Vista previa - Registro organizador"
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
