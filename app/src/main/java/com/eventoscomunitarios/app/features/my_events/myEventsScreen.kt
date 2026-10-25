package com.eventoscomunitarios.app.features.my_events

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.EventRegistrationStatus
import com.eventoscomunitarios.app.core.data.FakeEvents
import com.eventoscomunitarios.app.ui.theme.AppeventoscomunitariosTheme
import com.eventoscomunitarios.app.ui.theme.TextoPrincipal
import com.eventoscomunitarios.app.ui.theme.TextoSecundario

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyEventsScreen(
    onNavigateBack: () -> Unit,
    onAdministrarClick: (Int) -> Unit,
    viewModel: MyEventsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    MyEventsContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onTabSelected = viewModel::onTabSelected,
        onCancelEnrollment = viewModel::cancelEnrollment,
        onAdministrarClick = onAdministrarClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyEventsContent(
    state: MyEventsUiState,
    onNavigateBack: () -> Unit,
    onTabSelected: (MyEventsTab) -> Unit,
    onCancelEnrollment: (Int) -> Unit,
    onAdministrarClick: (Int) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis eventos", color = TextoPrincipal, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = TextoPrincipal)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF7F9FC)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            TabRow(
                selectedTabIndex = state.selectedTab.ordinal,
                containerColor = Color.White,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = state.selectedTab == MyEventsTab.ENROLLED,
                    onClick = { onTabSelected(MyEventsTab.ENROLLED) },
                    text = { Text("Inscritos") }
                )
                Tab(
                    selected = state.selectedTab == MyEventsTab.CREATED,
                    onClick = { onTabSelected(MyEventsTab.CREATED) },
                    text = { Text("Creados") }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.error != null) {
                    item { EmptyStateText(state.error) }
                } else if (state.selectedTab == MyEventsTab.ENROLLED) {
                    if (state.enrolledEvents.isEmpty()) {
                        item { EmptyStateText("No estás inscrito en ningún evento.") }
                    } else {
                        items(state.enrolledEvents, key = { it.event.id }) { enrolled ->
                            EnrolledEventCard(
                                enrolledEvent = enrolled,
                                onCancel = { onCancelEnrollment(enrolled.event.id) }
                            )
                        }
                    }
                } else {
                    if (state.createdEvents.isEmpty()) {
                        item { EmptyStateText("Aún no has creado ningún evento.") }
                    } else {
                        items(state.createdEvents, key = { it.id }) { event ->
                            CreatedEventCard(
                                event = event,
                                onAdministrarClick = { onAdministrarClick(event.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnrolledEventCard(enrolledEvent: EnrolledEvent, onCancel: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE0E6EB)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFFD9ECFF), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.DateRange,
                    contentDescription = null,
                    tint = Color(0xFF1E3A8A)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = enrolledEvent.event.title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = TextoPrincipal
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${enrolledEvent.event.dateLabel} · ${enrolledEvent.event.timeLabel}",
                    fontSize = 12.sp,
                    color = TextoSecundario
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = enrolledEvent.event.location,
                    fontSize = 11.sp,
                    color = TextoSecundario
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                val isConfirmed = enrolledEvent.status == EventRegistrationStatus.CONFIRMED
                val bgStatus = if (isConfirmed) Color(0xFFBFF2B2) else Color(0xFFFFDAB3)
                val textStatusColor = if (isConfirmed) Color(0xFF1B5E20) else Color(0xFF8D4004)
                val textStatus = if (isConfirmed) "Confirmado" else "En espera"

                Surface(color = bgStatus, shape = RoundedCornerShape(12.dp)) {
                    Text(
                        text = textStatus,
                        color = textStatusColor,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    color = Color(0xFFFFD6D6),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.clickable { onCancel() }
                ) {
                    Text(
                        text = "Cancelar",
                        color = Color(0xFF9E1C1C),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CreatedEventCard(event: Event, onAdministrarClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE0E6EB)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFFDDEFEA), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.DateRange,
                    contentDescription = null,
                    tint = Color(0xFF26733A)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = TextoPrincipal
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${event.attendeeCount} asistentes",
                    fontSize = 12.sp,
                    color = TextoSecundario
                )
            }
            Surface(
                color = Color(0xFFF0F4F8),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.clickable { onAdministrarClick() }
            ) {
                Text(
                    text = "Administrar",
                    color = Color(0xFF2B5B7E),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyStateText(message: String) {
    Text(
        text = message,
        fontSize = 14.sp,
        color = TextoSecundario,
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun MyEventsScreenPreview() {
    AppeventoscomunitariosTheme {
        MyEventsContent(
            state = MyEventsUiState(
                enrolledEvents = listOf(
                    EnrolledEvent(FakeEvents.events.first(), EventRegistrationStatus.CONFIRMED)
                ),
                createdEvents = listOf(FakeEvents.events.first())
            ),
            onNavigateBack = {},
            onTabSelected = {},
            onCancelEnrollment = {},
            onAdministrarClick = {}
        )
    }
}
