package com.eventoscomunitarios.app.features.create_event

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.eventoscomunitarios.app.core.components.EventCategorySelector
import com.eventoscomunitarios.app.core.components.EventDateTimeFields
import com.eventoscomunitarios.app.core.components.EventPhotoSelector
import com.eventoscomunitarios.app.core.components.EventTextField
import com.eventoscomunitarios.app.core.components.LoadingIndicator
import com.eventoscomunitarios.app.core.util.RequestResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEventScreen(
    onCloseClick: () -> Unit,
    onEventCreated: () -> Unit,
    viewModel: CreateEventViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Crear evento") }, navigationIcon = {
                IconButton(onClick = onCloseClick) { Icon(Icons.Default.Close, "Cerrar") }
            })
        },
        bottomBar = {
            Button(
                onClick = viewModel::publishEvent,
                enabled = state.publishResult != RequestResult.Loading && state.publishResult != RequestResult.Success,
                modifier = Modifier.fillMaxWidth().padding(16.dp).imePadding()
            ) { Text("Publicar evento") }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(16.dp).testTag("create_event_screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EventPhotoSelector(state.photoUri, viewModel::onPhotoSelected)
            EventTextField(state.title, viewModel::onTitleChange, "Título del evento", state.titleError)
            EventCategorySelector(state.category, state.categoryError, viewModel::onCategoryChange)
            EventDateTimeFields("Inicio", state.date, state.time, state.dateError, state.timeError,
                viewModel::onDateChange, viewModel::onTimeChange)
            EventDateTimeFields("Finalización", state.endDate, state.endTime, state.endDateError, state.endTimeError,
                viewModel::onEndDateChange, viewModel::onEndTimeChange)
            EventTextField(state.address, viewModel::onAddressChange, "Dirección", state.addressError)
            EventTextField(state.description, viewModel::onDescriptionChange, "Descripción", state.descriptionError, minLines = 3)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EventTextField(state.capacity, viewModel::onCapacityChange, "Cupo", state.capacityError, Modifier.weight(1f), numeric = true)
                EventTextField(state.price, viewModel::onPriceChange, "Precio (COP)", state.priceError,
                    Modifier.weight(1f), numeric = true, enabled = !state.isFree)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Evento gratuito")
                Switch(checked = state.isFree, onCheckedChange = viewModel::onFreeChange)
            }
            Text("Cuando se complete el cupo, las personas podrán unirse a la lista de espera.",
                style = MaterialTheme.typography.bodySmall)
            (state.publishResult as? RequestResult.Failure)?.let {
                Text(it.message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (state.publishResult == RequestResult.Loading) LoadingIndicator()
    if (state.publishResult == RequestResult.Success) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Evento enviado a revisión") },
            text = { Text("Podrás seguir su estado en Mis eventos. Será visible en el inicio cuando un moderador lo apruebe.") },
            confirmButton = { TextButton(onClick = { viewModel.clearPublishResult(); onEventCreated() }) { Text("Aceptar") } }
        )
    }
}
