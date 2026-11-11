package com.eventoscomunitarios.app.features.profile

import com.eventoscomunitarios.app.core.util.localizedMessage

import androidx.compose.ui.res.stringResource
import com.eventoscomunitarios.app.R

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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
import com.eventoscomunitarios.app.domain.model.OrganizerVerificationStatus
import com.eventoscomunitarios.app.core.util.AppLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userId: String,
    onNavigateBack: () -> Unit,
    onCerrarSesion: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
    onNotificationsClick: () -> Unit = {},
    onAccountDeleted: () -> Unit = onCerrarSesion,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val snackbar = snackbarHostState
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> viewModel.onPhotoSelected(uri?.toString()) }
    val identityPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> viewModel.submitOrganizerIdentity(uri?.toString()) }
    BackHandler(enabled = state.isBusy) { }
    LaunchedEffect(userId) { viewModel.loadUser(userId) }
    LaunchedEffect(state.accountDeleted) { if (state.accountDeleted) onAccountDeleted() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(context.localizedMessage(it))
            viewModel.clearMessage()
        }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text(stringResource(R.string.ui_profile)) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack, enabled = !state.isBusy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ui_back)) }
            },
            actions = {
                IconButton(onClick = onNotificationsClick, enabled = !state.isBusy) { Icon(Icons.Default.Notifications, contentDescription = stringResource(R.string.ui_notifications)) }
            }
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.loadResult == RequestResult.Loading) {
                LoadingIndicator()
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val user = state.user
                    if (user == null) {
                        Text(localizedMessage((state.loadResult as? RequestResult.Failure)?.message ?: stringResource(R.string.ui_profile_unavailable)))
                        Button(onClick = onCerrarSesion) { Text(stringResource(R.string.ui_go_home)) }
                    } else {
                        state.syncError?.let { Text(localizedMessage(it), color = MaterialTheme.colorScheme.error) }
                        Box(
                            modifier = Modifier.size(88.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            val imageUri = if (state.isEditing && state.removePhoto) null else state.selectedPhotoUri ?: user.profilePictureUrl
                            if (imageUri == null) Text(user.name.initials(), style = MaterialTheme.typography.headlineMedium)
                            else RemoteImage(imageUri, stringResource(R.string.ui_profile_photo), Modifier.size(88.dp).clip(CircleShape))
                        }
                        Text(user.name, style = MaterialTheme.typography.titleLarge)
                        Text(when (user.role) {
                            UserRole.PARTICIPANT -> stringResource(R.string.ui_participant)
                            UserRole.ORGANIZER -> stringResource(R.string.ui_organizer)
                            UserRole.ADMINISTRATOR -> stringResource(R.string.ui_administrator)
                        }, style = MaterialTheme.typography.labelLarge)
                        if (user.role == UserRole.ORGANIZER) {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(stringResource(when (user.organizerVerificationStatus) {
                                        OrganizerVerificationStatus.VERIFIED -> R.string.profile_identity_verified
                                        OrganizerVerificationStatus.REJECTED -> R.string.profile_identity_rejected
                                        else -> R.string.profile_identity_pending
                                    }))
                                    if (user.organizerVerificationStatus != OrganizerVerificationStatus.VERIFIED) {
                                        OutlinedButton(onClick = { identityPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                            enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) {
                                            Text(stringResource(R.string.profile_identity_upload))
                                        }
                                    }
                                }
                            }
                        }
                        if (state.isEditing) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !state.isBusy) { Text(stringResource(R.string.ui_pick_photo)) }
                                TextButton(onClick = viewModel::removePhoto, enabled = !state.isBusy) { Text(stringResource(R.string.ui_remove_photo)) }
                            }
                            OutlinedTextField(
                                value = state.name, onValueChange = viewModel::onNameChange,
                                modifier = Modifier.fillMaxWidth(), enabled = !state.isBusy, label = { Text(stringResource(R.string.ui_name)) }, singleLine = true,
                                isError = state.errors["name"] != null,
                                supportingText = state.errors["name"]?.let { error -> { Text(localizedMessage(error)) } }
                            )
                            OutlinedTextField(
                                value = state.email, onValueChange = viewModel::onEmailChange,
                                modifier = Modifier.fillMaxWidth(), enabled = !state.isBusy, label = { Text(stringResource(R.string.ui_email)) }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                isError = state.errors["email"] != null,
                                supportingText = state.errors["email"]?.let { error -> { Text(localizedMessage(error)) } }
                            )
                            if (!state.email.trim().equals(user.email, true)) {
                                Text(stringResource(R.string.ui_email_change_help), style = MaterialTheme.typography.bodySmall)
                                OutlinedTextField(value = state.reauthPassword, onValueChange = viewModel::onReauthPasswordChange,
                                    modifier = Modifier.fillMaxWidth(), enabled = !state.isBusy, label = { Text(stringResource(R.string.ui_current_password)) },
                                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    isError = state.errors["password"] != null,
                                    supportingText = state.errors["password"]?.let { error -> { Text(localizedMessage(error)) } })
                            }
                            OutlinedTextField(
                                value = state.phoneNumber, onValueChange = viewModel::onPhoneChange,
                                modifier = Modifier.fillMaxWidth(), enabled = !state.isBusy, label = { Text(stringResource(R.string.ui_optional_phone)) }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                isError = state.errors["phone"] != null,
                                supportingText = state.errors["phone"]?.let { error -> { Text(localizedMessage(error)) } }
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = viewModel::cancelEditing, enabled = !state.isBusy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.ui_cancel)) }
                                Button(onClick = viewModel::saveProfile, enabled = !state.isBusy, modifier = Modifier.weight(1f)) { Text(if (state.isBusy) stringResource(R.string.ui_saving) else stringResource(R.string.ui_save)) }
                            }
                        } else {
                            Text(user.email)
                            user.pendingEmail?.let { Text(stringResource(R.string.ui_pending_email, it), style = MaterialTheme.typography.bodySmall) }
                            if (user.pendingEmail != null) OutlinedButton(onClick = viewModel::refreshAccount, enabled = !state.isBusy) { Text(if (state.isBusy) stringResource(R.string.ui_refreshing) else stringResource(R.string.ui_refresh_account)) }
                            user.phoneNumber?.let { Text(it) }
                            Text(stringResource(R.string.ui_registered_date, java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, locale).format(user.registrationDate)),
                                style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = viewModel::startEditing, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_edit_profile)) }
                        }
                        OutlinedButton(onClick = onNotificationsClick, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.ui_profile_notifications, state.unreadNotifications))
                        }
                        state.statistics?.let { stats ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(stringResource(R.string.ui_reputation), style = MaterialTheme.typography.titleMedium)
                                    Text(stringResource(R.string.ui_reputation_points, stats.reputation), style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    Text(stringResource(R.string.ui_reputation_help),
                                        style = MaterialTheme.typography.bodySmall)
                                    HorizontalDivider()
                                    StatisticRow(stringResource(R.string.ui_publications), stats.publications)
                                    StatisticRow(stringResource(R.string.ui_approved_posts), stats.approvedPublications)
                                    StatisticRow(stringResource(R.string.ui_finished_events), stats.finishedEvents)
                                    StatisticRow(stringResource(R.string.ui_confirmed_attendances), stats.confirmedAttendances)
                                    StatisticRow(stringResource(R.string.ui_waitlisted), stats.waitlistedAttendances)
                                    StatisticRow(stringResource(R.string.ui_comments_posted), stats.comments)
                                    StatisticRow(stringResource(R.string.ui_net_votes), stats.receivedVotes)
                                }
                            }
                        }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.profile_language), style = MaterialTheme.typography.titleMedium)
                                listOf("" to R.string.profile_language_system, "es" to R.string.profile_language_es,
                                    "en" to R.string.profile_language_en).forEach { (tag, label) ->
                                    Row(Modifier.fillMaxWidth().selectable(selected = state.languageTag == tag,
                                        enabled = !state.isBusy, role = Role.RadioButton,
                                        onClick = {
                                            if (state.languageTag != tag) {
                                                viewModel.onLanguageSelected(tag)
                                                AppLanguage.apply(context, tag)
                                            }
                                        }).testTag("profile_language_${tag.ifEmpty { "system" }}").padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        RadioButton(selected = state.languageTag == tag, onClick = null, enabled = !state.isBusy)
                                        Text(stringResource(label))
                                    }
                                }
                                Text(stringResource(R.string.profile_language_help), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Button(onClick = onCerrarSesion, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_sign_out)) }
                        TextButton(onClick = viewModel::requestDeleteAccount, enabled = !state.isBusy,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.ui_delete_my_account)) }
                    }
                }
            }
        }
    }
    if (state.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteAccount,
            title = { Text(stringResource(R.string.ui_delete_my_account)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.ui_delete_account_help))
                    OutlinedTextField(value = state.deletePassword, onValueChange = viewModel::onDeletePasswordChange,
                        enabled = !state.isBusy, label = { Text(stringResource(R.string.ui_current_password)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = state.errors["deletePassword"] != null,
                        supportingText = state.errors["deletePassword"]?.let { error -> { Text(localizedMessage(error)) } })
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::deleteAccount, enabled = !state.isBusy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(if (state.isBusy) stringResource(R.string.ui_deleting) else stringResource(R.string.ui_delete_account)) }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissDeleteAccount, enabled = !state.isBusy) { Text(stringResource(R.string.ui_keep_account)) } }
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
