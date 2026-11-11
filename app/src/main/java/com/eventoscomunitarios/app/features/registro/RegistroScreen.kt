package com.eventoscomunitarios.app.features.registro

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.eventoscomunitarios.app.core.util.localizedMessage
import com.eventoscomunitarios.app.R
import com.eventoscomunitarios.app.core.components.RemoteImage
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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.domain.model.User
import com.eventoscomunitarios.app.core.components.LoadingIndicator
import com.eventoscomunitarios.app.ui.theme.AppeventoscomunitariosTheme
import com.eventoscomunitarios.app.ui.theme.AzulClaro
import com.eventoscomunitarios.app.ui.theme.Fondo
import com.eventoscomunitarios.app.ui.theme.Gris
import com.eventoscomunitarios.app.ui.theme.TextoPrincipal
import com.eventoscomunitarios.app.ui.theme.TextoSecundario

@Composable
fun RegistroScreen(
    viewModel: RegistroViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onRegistroExitoso: (User) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.registroResult) {
        when (val result = state.registroResult) {
            RequestResult.Success -> {
                val registeredUser = state.registeredUser
                viewModel.clearRegistroResult()
                registeredUser?.let(onRegistroExitoso)
            }

            is RequestResult.Failure -> {
                snackbarHostState.showSnackbar(context.localizedMessage(result.message))
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
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)) {
            RegistroContent(
                state = state,
                onNavigateBack = onNavigateBack,
                onNombreChange = viewModel::onNombreChange,
                onEmailChange = viewModel::onEmailChange,
                onTelefonoChange = viewModel::onTelefonoChange,
                onPasswordChange = viewModel::onPasswordChange,
                onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
                onAccountTypeChange = viewModel::onAccountTypeChange,
                onIdentityPhotoSelected = viewModel::onIdentityPhotoSelected,
                onTerminosChange = viewModel::onTerminosChange,
                onContinuar = viewModel::onContinuarClick,
                modifier = Modifier
            )
            if (state.registroResult == RequestResult.Loading) LoadingIndicator()
        }
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
    onContinuar: () -> Unit,
    onIdentityPhotoSelected: (String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val identityPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        onIdentityPhotoSelected(it?.toString())
    }
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
                        contentDescription = stringResource(R.string.verification_ui_back),
                        tint = TextoPrincipal
                    )
                }

                Text(
                    text = stringResource(R.string.verification_ui_register),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextoPrincipal
                )
            }

            Text(stringResource(R.string.verification_ui_personal_details), color = azulPrincipal, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                    RegistroField(
                        value = state.nombre,
                        onValueChange = onNombreChange,
                        label = stringResource(R.string.verification_ui_name),
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
                        label = stringResource(R.string.verification_ui_email),
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
                        label = stringResource(R.string.verification_ui_phone),
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
                        label = stringResource(R.string.verification_ui_password),
                        placeholder = "",
                        error = state.passwordError,
                        supportingText = if (state.passwordError == null) {
                            stringResource(R.string.verification_ui_password_hint)
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
                                        stringResource(R.string.verification_ui_hide_password)
                                    } else {
                                        stringResource(R.string.verification_ui_show_password)
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
                        label = stringResource(R.string.verification_ui_confirm_password),
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
                        text = stringResource(R.string.verification_ui_account_type),
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
                            text = stringResource(R.string.verification_ui_participant),
                            selected = !state.isOrganizer,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onAccountTypeChange(false)
                            }
                        )

                        RoleOption(
                            text = stringResource(R.string.verification_ui_organizer),
                            selected = state.isOrganizer,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onAccountTypeChange(true)
                            }
                        )
                    }

                    if (state.isOrganizer) {
                        Text(stringResource(R.string.verification_identity_label), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.verification_registration_explanation), style = MaterialTheme.typography.bodySmall)
                        state.identityPhotoUri?.let {
                            RemoteImage(it, stringResource(R.string.verification_identity_label), Modifier.fillMaxWidth().height(160.dp))
                        }
                        TextButton(onClick = { identityPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                            Text(stringResource(if (state.identityPhotoUri == null) R.string.verification_choose_identity else R.string.verification_replace_identity))
                        }
                        state.identityPhotoError?.let { Text(localizedMessage(it), color = MaterialTheme.colorScheme.error) }
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
                            text = stringResource(R.string.verification_ui_terms),
                            fontSize = 11.sp,
                            color = TextoSecundario
                        )
                    }
                    state.terminosError?.let {
                        Text(localizedMessage(it), color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onContinuar,
                enabled = state.isFormValid &&
                        state.registroResult != RequestResult.Loading,
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
                    text = stringResource(R.string.verification_ui_register),
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
                error != null -> Text(localizedMessage(error))
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

@Preview(
    showBackground = true,
    showSystemUi = true,
    name = "Vista previa - Registro"
)
@Composable
private fun RegistroScreenPreview() {
    AppeventoscomunitariosTheme(
        darkTheme = false,
        dynamicColor = false
    ) {
        RegistroContent(
            state = RegistroUiState(),
            onNavigateBack = {},
            onNombreChange = {},
            onEmailChange = {},
            onTelefonoChange = {},
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onAccountTypeChange = {},
            onTerminosChange = {},
            onContinuar = {}
        )
    }
}
