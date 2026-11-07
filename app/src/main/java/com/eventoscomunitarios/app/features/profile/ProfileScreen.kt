package com.eventoscomunitarios.app.features.profile

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.eventoscomunitarios.app.core.components.LoadingIndicator
import com.eventoscomunitarios.app.core.components.RemoteImage
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.core.util.initials
import com.eventoscomunitarios.app.domain.model.UserRole
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userId: String,
    onNavigateBack: () -> Unit,
    onCerrarSesion: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
    onNotificationsClick: () -> Unit = {},
    onAccountDeleted: () -> Unit = onCerrarSesion
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> viewModel.onPhotoSelected(uri?.toString()) }
    BackHandler(enabled = state.isBusy) { }
    LaunchedEffect(userId) { viewModel.loadUser(userId) }
    LaunchedEffect(state.accountDeleted) { if (state.accountDeleted) onAccountDeleted() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Mi perfil") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = !state.isBusy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                },
                actions = {
                    IconButton(onClick = onNotificationsClick, enabled = !state.isBusy) { Icon(Icons.Default.Notifications, contentDescription = "Notificaciones") }
                }
            )
        }
    ) { padding ->
        if (state.loadResult == RequestResult.Loading) {
            LoadingIndicator()
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val user = state.user
                if (user == null) {
                    Text((state.loadResult as? RequestResult.Failure)?.message ?: "El perfil ya no está disponible")
                    Button(onClick = onCerrarSesion) { Text("Ir al inicio") }
                } else {
                    state.syncError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Box(
                        modifier = Modifier.size(88.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val imageUri = if (state.isEditing && state.removePhoto) null else state.selectedPhotoUri ?: user.profilePictureUrl
                        if (imageUri == null) Text(user.name.initials(), style = MaterialTheme.typography.headlineMedium)
                        else RemoteImage(imageUri, "Foto de perfil", Modifier.size(88.dp).clip(CircleShape))
                    }
                    Text(user.name, style = MaterialTheme.typography.titleLarge)
                    Text(when (user.role) {
                        UserRole.PARTICIPANT -> "Participante"
                        UserRole.ORGANIZER -> "Organizador"
                        UserRole.ADMINISTRATOR -> "Moderador"
                    }, style = MaterialTheme.typography.labelLarge)
                    if (state.isEditing) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !state.isBusy) { Text("Elegir foto") }
                            TextButton(onClick = viewModel::removePhoto, enabled = !state.isBusy) { Text("Quitar foto") }
                        }
                        OutlinedTextField(
                            value = state.name, onValueChange = viewModel::onNameChange,
                            modifier = Modifier.fillMaxWidth(), enabled = !state.isBusy, label = { Text("Nombre") }, singleLine = true,
                            isError = state.errors["name"] != null,
                            supportingText = state.errors["name"]?.let { error -> { Text(error) } }
                        )
                        OutlinedTextField(
                            value = state.email, onValueChange = viewModel::onEmailChange,
                            modifier = Modifier.fillMaxWidth(), enabled = !state.isBusy, label = { Text("Correo electrónico") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            isError = state.errors["email"] != null,
                            supportingText = state.errors["email"]?.let { error -> { Text(error) } }
                        )
                        if (!state.email.trim().equals(user.email, true)) {
                            Text("El nuevo correo se aplicará después de que confirmes el enlace enviado a esa dirección.", style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(value = state.reauthPassword, onValueChange = viewModel::onReauthPasswordChange,
                                modifier = Modifier.fillMaxWidth(), enabled = !state.isBusy, label = { Text("Contraseña actual") },
                                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                isError = state.errors["password"] != null,
                                supportingText = state.errors["password"]?.let { error -> { Text(error) } })
                        }
                        OutlinedTextField(
                            value = state.phoneNumber, onValueChange = viewModel::onPhoneChange,
                            modifier = Modifier.fillMaxWidth(), enabled = !state.isBusy, label = { Text("Teléfono (opcional)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            isError = state.errors["phone"] != null,
                            supportingText = state.errors["phone"]?.let { error -> { Text(error) } }
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = viewModel::cancelEditing, enabled = !state.isBusy, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                            Button(onClick = viewModel::saveProfile, enabled = !state.isBusy, modifier = Modifier.weight(1f)) { Text(if (state.isBusy) "Guardando…" else "Guardar") }
                        }
                    } else {
                        Text(user.email)
                        user.pendingEmail?.let { Text("Pendiente de verificar: $it", style = MaterialTheme.typography.bodySmall) }
                        if (user.pendingEmail != null) OutlinedButton(onClick = viewModel::refreshAccount, enabled = !state.isBusy) { Text(if (state.isBusy) "Actualizando…" else "Actualizar cuenta") }
                        user.phoneNumber?.let { Text(it) }
                        Text("Registro: ${SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("es-CO")).format(user.registrationDate)}",
                            style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = viewModel::startEditing, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) { Text("Editar perfil") }
                    }
                    OutlinedButton(onClick = onNotificationsClick, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) {
                        Text("Notificaciones · ${state.unreadNotifications} sin leer")
                    }
                    state.statistics?.let { stats ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Estadísticas y reputación", style = MaterialTheme.typography.titleMedium)
                                Text("${stats.reputation} puntos", style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text("La reputación suma el balance de votos recibidos y 5 puntos por evento finalizado, con un mínimo de cero.",
                                    style = MaterialTheme.typography.bodySmall)
                                HorizontalDivider()
                                StatisticRow("Publicaciones", stats.publications)
                                StatisticRow("Publicaciones aprobadas", stats.approvedPublications)
                                StatisticRow("Eventos finalizados", stats.finishedEvents)
                                StatisticRow("Asistencias confirmadas", stats.confirmedAttendances)
                                StatisticRow("En lista de espera", stats.waitlistedAttendances)
                                StatisticRow("Comentarios realizados", stats.comments)
                                StatisticRow("Balance de votos recibidos", stats.receivedVotes)
                            }
                        }
                    }
                    Button(onClick = onCerrarSesion, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) { Text("Cerrar sesión") }
                    TextButton(onClick = viewModel::requestDeleteAccount, enabled = !state.isBusy,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Eliminar mi cuenta") }
                }
            }
        }
    }
    if (state.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteAccount,
            title = { Text("Eliminar mi cuenta") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Se eliminarán tu perfil, tus publicaciones, comentarios, votos e inscripciones. Los cupos que liberes se asignarán a las personas en espera. Esta acción no se puede deshacer.")
                    OutlinedTextField(value = state.deletePassword, onValueChange = viewModel::onDeletePasswordChange,
                        enabled = !state.isBusy, label = { Text("Contraseña actual") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = state.errors["deletePassword"] != null,
                        supportingText = state.errors["deletePassword"]?.let { error -> { Text(error) } })
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::deleteAccount, enabled = !state.isBusy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(if (state.isBusy) "Eliminando…" else "Eliminar cuenta") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissDeleteAccount, enabled = !state.isBusy) { Text("Conservar cuenta") } }
        )
    }
}

@Composable
private fun StatisticRow(label: String, value: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value.toString(), fontWeight = FontWeight.SemiBold)
    }
}
