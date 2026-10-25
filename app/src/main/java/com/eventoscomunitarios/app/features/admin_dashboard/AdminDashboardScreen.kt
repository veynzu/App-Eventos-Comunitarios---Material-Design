package com.eventoscomunitarios.app.features.admin_dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventoscomunitarios.app.core.components.AdminBottomBar
import com.eventoscomunitarios.app.core.components.AdminDestination
import com.eventoscomunitarios.app.core.components.LoadingIndicator
import com.eventoscomunitarios.app.core.components.ModerationEventCard
import com.eventoscomunitarios.app.core.data.FakeEvents
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.ui.theme.AppeventoscomunitariosTheme
import com.eventoscomunitarios.app.ui.theme.Fondo
import com.eventoscomunitarios.app.ui.theme.TextoPrincipal
import com.eventoscomunitarios.app.ui.theme.TextoSecundario

@Composable
fun AdminDashboardScreen(
    viewModel: AdminDashboardViewModel = viewModel(),
    onModerationClick: () -> Unit = {},
    onUsersClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onEventDetail: (Int) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    AdminDashboardContent(
        state = state,
        onApprove = viewModel::approve,
        onReject = viewModel::reject,
        onDismissResult = viewModel::clearActionResult,
        onModerationClick = onModerationClick,
        onUsersClick = onUsersClick,
        onProfileClick = onProfileClick,
        onEventDetail = onEventDetail
    )
}

@Composable
private fun AdminDashboardContent(
    state: AdminDashboardUiState,
    onApprove: (Int) -> Unit,
    onReject: (Int) -> Unit,
    onDismissResult: () -> Unit,
    onModerationClick: () -> Unit,
    onUsersClick: () -> Unit,
    onProfileClick: () -> Unit,
    onEventDetail: (Int) -> Unit
) {
    when (val result = state.loadResult) {
        RequestResult.Loading -> return LoadingIndicator()
        is RequestResult.Failure -> return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(result.message) }
        RequestResult.Success -> Unit
    }
    Scaffold(
        containerColor = Fondo,
        bottomBar = {
            AdminBottomBar(
                selected = AdminDestination.DASHBOARD,
                onDashboardClick = {}, onModerationClick = onModerationClick,
                onUsersClick = onUsersClick, onProfileClick = onProfileClick
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Panel de control", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextoPrincipal)
                        Text("Armenia, Quindío · 23 sep 2026", fontSize = 12.sp, color = TextoSecundario)
                    }
                    Box {
                        IconButton(onClick = {}) { Icon(Icons.Default.Notifications, "Notificaciones") }
                        Badge(Modifier.align(Alignment.TopEnd)) { Text(state.pendingEvents.size.toString()) }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard("${state.activeEvents}", "Eventos activos", "+12%", Modifier.weight(1f))
                        MetricCard("${state.pendingEvents.size}", "Por aprobar", "Hoy", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard("${state.totalUsers}", "Usuarios", "+38", Modifier.weight(1f))
                        MetricCard("${state.totalAttendance}", "Asistencias", "+9%", Modifier.weight(1f))
                    }
                }
            }
            item { WeeklyChart(state.weeklyAttendance) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Requieren tu atención", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onModerationClick) { Text("Ver todo") }
                }
            }
            if (state.pendingEvents.isEmpty()) {
                item { Text("No hay eventos pendientes de moderación.", color = TextoSecundario) }
            } else {
                items(state.pendingEvents.take(2), key = { it.id }) { event ->
                    ModerationEventCard(
                        event = event, compact = true,
                        onReject = { onReject(event.id) },
                        onDetail = { onEventDetail(event.id) },
                        onApprove = { onApprove(event.id) }
                    )
                }
            }
        }
    }

    if (state.actionResult == RequestResult.Loading) LoadingIndicator()
    if (state.actionResult == RequestResult.Success) {
        AlertDialog(
            onDismissRequest = onDismissResult,
            icon = { Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(48.dp)) },
            title = {
                Text(if (state.actionMessage?.contains("aprobado", true) == true) "¡Evento aprobado!" else "Evento rechazado")
            },
            text = { Text(state.actionMessage.orEmpty()) },
            confirmButton = { TextButton(onClick = onDismissResult) { Text("Aceptar") } },
            modifier = Modifier.testTag("admin_action_success")
        )
    }
}

@Composable
private fun MetricCard(value: String, label: String, trend: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextoPrincipal)
            Text(label, fontSize = 12.sp, color = TextoSecundario)
            Text(trend, fontSize = 11.sp, color = Color(0xFF2E7D32))
        }
    }
}

@Composable
private fun WeeklyChart(values: List<Int>) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Asistencias por semana", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                val max = (values.maxOrNull() ?: 1).toFloat()
                val slot = size.width / values.size
                values.forEachIndexed { index, value ->
                    val height = size.height * value / max
                    drawLine(
                        color = if (index == values.lastIndex) Color(0xFF2B638B) else Color(0xFFB8D7F2),
                        start = Offset(slot * index + slot / 2, size.height),
                        end = Offset(slot * index + slot / 2, size.height - height),
                        strokeWidth = slot * .5f
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                (30..35).forEach { Text("S$it", fontSize = 10.sp, color = TextoSecundario) }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AdminDashboardPreview() {
    AppeventoscomunitariosTheme(darkTheme = false, dynamicColor = false) {
        AdminDashboardContent(
            state = AdminDashboardUiState(
                loadResult = RequestResult.Success,
                activeEvents = 48,
                pendingEvents = FakeEvents.events.takeLast(3),
                totalUsers = 1284,
                totalAttendance = 3107
            ),
            onApprove = {}, onReject = {}, onDismissResult = {}, onModerationClick = {},
            onUsersClick = {}, onProfileClick = {}, onEventDetail = {}
        )
    }
}
