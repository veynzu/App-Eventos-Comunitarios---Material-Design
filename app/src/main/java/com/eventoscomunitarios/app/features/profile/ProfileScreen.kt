package com.eventoscomunitarios.app.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventoscomunitarios.app.core.components.LoadingIndicator
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.core.util.initials
import com.eventoscomunitarios.app.domain.model.UserRole
import com.eventoscomunitarios.app.features.auth.FakeUsers
import com.eventoscomunitarios.app.ui.theme.AppeventoscomunitariosTheme
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ProfileScreen(
    userId: String,
    onNavigateBack: () -> Unit,
    onCerrarSesion: () -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(userId) { viewModel.loadUser(userId) }

    ProfileContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onCerrarSesion = onCerrarSesion
    )
}

@Composable
private fun ProfileContent(
    state: ProfileUiState,
    onNavigateBack: () -> Unit,
    onCerrarSesion: () -> Unit
) {
    if (state.loadResult == RequestResult.Loading) {
        LoadingIndicator()
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextButton(onClick = onNavigateBack, modifier = Modifier.align(Alignment.Start)) {
            Text("Volver al inicio")
        }
        Text("Mi perfil", style = MaterialTheme.typography.headlineMedium)
        state.user?.let { user ->
            Box(
                modifier = Modifier.size(88.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(user.name.initials(), style = MaterialTheme.typography.headlineMedium)
            }
            Text(user.name, style = MaterialTheme.typography.titleLarge)
            Text(user.email)
            user.phoneNumber?.let { Text(it) }
            Text(when (user.role) {
                UserRole.PARTICIPANT -> "Participante"
                UserRole.ORGANIZER -> "Organizador"
                UserRole.ADMINISTRATOR -> "Administrador"
            })
            Text("Registro: ${SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("es-CO")).format(user.registrationDate)}")
        }
        state.errors.values.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onCerrarSesion, modifier = Modifier.fillMaxWidth()) {
            Text("Cerrar sesión")
        }
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    name = "Vista previa - Perfil"
)
@Composable
private fun ProfileScreenPreview() {
    AppeventoscomunitariosTheme(darkTheme = false, dynamicColor = false) {
        ProfileContent(
            state = ProfileUiState(
                user = FakeUsers.users.first(),
                loadResult = RequestResult.Success
            ),
            onNavigateBack = {},
            onCerrarSesion = {}
        )
    }
}
