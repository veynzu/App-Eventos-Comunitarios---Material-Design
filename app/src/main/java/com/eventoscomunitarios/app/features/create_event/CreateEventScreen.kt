package com.eventoscomunitarios.app.features.create_event

import com.eventoscomunitarios.app.core.util.localizedMessage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.eventoscomunitarios.app.features.locationpicker.LocationPickerDialog
import com.eventoscomunitarios.app.core.components.EventLocationField
import com.eventoscomunitarios.app.core.components.EventOutdoorField
import com.eventoscomunitarios.app.R
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEventScreen(
    onCloseClick: () -> Unit,
    onEventCreated: () -> Unit,
    viewModel: CreateEventViewModel = hiltViewModel(),
    onOpenSimilarEvent: (Int) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val language = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language.let { if (it == "en") "en" else "es" }
    androidx.activity.compose.BackHandler(enabled = state.publishResult == RequestResult.Loading) { }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(if (state.sourceEventId == null) stringResource(R.string.ui_create_event) else stringResource(R.string.new_event_edition)) }, navigationIcon = {
            IconButton(onClick = onCloseClick, enabled = state.publishResult != RequestResult.Loading) { Icon(Icons.Default.Close, stringResource(R.string.ui_close)) }
        })
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(16.dp).testTag("create_event_screen"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.sourceEventId != null) Text(stringResource(R.string.relaunch_event_help), style = MaterialTheme.typography.bodyMedium)
                EventPhotoSelector(state.photoUri, viewModel::onPhotoSelected)
                EventTextField(state.title, viewModel::onTitleChange, stringResource(R.string.ui_event_title), state.titleError)
                EventCategorySelector(state.category, state.categoryError, viewModel::onCategoryChange)
                EventOutdoorField(state.isOutdoor, viewModel::onOutdoorChange)
                EventDateTimeFields(stringResource(R.string.ui_start), state.date, state.time, state.dateError, state.timeError,
                    viewModel::onDateChange, viewModel::onTimeChange)
                EventDateTimeFields(stringResource(R.string.ui_end), state.endDate, state.endTime, state.endDateError, state.endTimeError,
                    viewModel::onEndDateChange, viewModel::onEndTimeChange)
                EventLocationField(state.address, state.coordinates, state.addressError, viewModel::openLocationPicker)
                EventTextField(state.description, viewModel::onDescriptionChange, stringResource(R.string.ui_description), state.descriptionError, minLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EventTextField(state.capacity, viewModel::onCapacityChange, stringResource(R.string.ui_capacity), state.capacityError, Modifier.weight(1f), numeric = true)
                    EventTextField(state.price, viewModel::onPriceChange, stringResource(R.string.ui_price), state.priceError,
                        Modifier.weight(1f), numeric = true, enabled = !state.isFree)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ui_free_event))
                    Switch(checked = state.isFree, onCheckedChange = viewModel::onFreeChange)
                }
                Text(stringResource(R.string.ui_waitlist_help),
                    style = MaterialTheme.typography.bodySmall)
                com.eventoscomunitarios.app.core.components.EventAnalysisFeedback(state.assessment, viewModel::useSuggestedDescription)
                (state.publishResult as? RequestResult.Failure)?.let {
                    Text(localizedMessage(it.message), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Button(
            onClick = { viewModel.publishEvent(language) },
            enabled = state.sourceReady && state.publishResult != RequestResult.Loading && state.publishResult != RequestResult.Success,
            modifier = Modifier.fillMaxWidth().padding(16.dp).imePadding()
        ) { Text(stringResource(if (state.isAnalyzing) R.string.ai_analyzing else R.string.ui_publish_event)) }
    }
    if (state.showLocationPicker) LocationPickerDialog(state.coordinates, state.address, viewModel::closeLocationPicker, viewModel::onLocationSelected)
    if (state.publishResult == RequestResult.Loading) LoadingIndicator()
    if (state.showDuplicateDialog) state.assessment?.let { review ->
        com.eventoscomunitarios.app.core.components.DuplicateEventsDialog(review, onOpenSimilarEvent,
            viewModel::confirmDifferentEvent, viewModel::dismissDuplicates)
    }
    if (state.publishResult == RequestResult.Success) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.ui_event_submitted)) },
            text = { Text(stringResource(R.string.ui_event_submitted_help)) },
            confirmButton = { TextButton(onClick = { viewModel.clearPublishResult(); onEventCreated() }) { Text(stringResource(R.string.ui_accept)) } }
        )
    }
}
