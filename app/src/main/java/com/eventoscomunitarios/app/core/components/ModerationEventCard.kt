package com.eventoscomunitarios.app.core.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.EventModerationStatus
import com.eventoscomunitarios.app.ui.theme.TextoPrincipal
import com.eventoscomunitarios.app.ui.theme.TextoSecundario

@Composable
fun ModerationEventCard(
    event: Event,
    compact: Boolean = false,
    onReject: () -> Unit,
    onDetail: () -> Unit,
    onApprove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(event.title, fontWeight = FontWeight.SemiBold, color = TextoPrincipal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${event.submittedByName} · ${event.submittedAtLabel}", fontSize = 12.sp, color = TextoSecundario, maxLines = 1)
                }
                if (!compact) AssistChip(onClick = {}, label = { Text(statusLabel(event.moderationStatus), fontSize = 11.sp) })
            }
            if (!compact) {
                Text("${event.dateLabel} · ${event.timeLabel} · ${event.location}", fontSize = 12.sp, color = TextoSecundario)
                AssistChip(onClick = {}, label = { Text(event.category.displayName, fontSize = 11.sp) })
            }
            when (event.moderationStatus) {
                EventModerationStatus.PENDING -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onReject) { Text("Rechazar") }
                    Spacer(Modifier.width(8.dp))
                    if (!compact) {
                        OutlinedButton(onClick = onDetail) { Text("Detalle") }
                        Spacer(Modifier.width(8.dp))
                    }
                    Button(
                        onClick = onApprove,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B638B))
                    ) { Text("Aprobar") }
                }
                else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onDetail) { Text("Ver detalle") }
                }
            }
        }
    }
}

private fun statusLabel(status: EventModerationStatus): String = when (status) {
    EventModerationStatus.PENDING -> "Pendiente"
    EventModerationStatus.APPROVED -> "Aprobado"
    EventModerationStatus.REJECTED -> "Rechazado"
}
