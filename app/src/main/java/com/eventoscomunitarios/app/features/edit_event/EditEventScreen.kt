package com.eventoscomunitarios.app.features.edit_event

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventoscomunitarios.app.core.components.LoadingIndicator
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.ui.theme.AppeventoscomunitariosTheme
import java.util.Calendar

@Composable
fun EditEventScreen(
    onCloseClick: () -> Unit,
    onChangesSaved: () -> Unit,
    viewModel: EditEventViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val calendar = remember { Calendar.getInstance() }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onPhotoSelected(uri?.toString())
    }
    val selectDate = {
        DatePickerDialog(
            context,
            { _, year, month, day -> viewModel.onDateChange("%02d/%02d/%04d".format(day, month + 1, year)) },
            calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
    val selectTime = {
        TimePickerDialog(
            context,
            { _, hour, minute ->
                val suffix = if (hour >= 12) "p.m." else "a.m."
                val hour12 = if (hour % 12 == 0) 12 else hour % 12
                viewModel.onTimeChange("%d:%02d %s".format(hour12, minute, suffix))
            },
            calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false
        ).show()
    }

    EditEventContent(
        state = state,
        onCloseClick = onCloseClick,
        onPhotoClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onTitleChange = viewModel::onTitleChange,
        onCategoryChange = viewModel::onCategoryChange,
        onDateClick = selectDate,
        onTimeClick = selectTime,
        onAddressChange = viewModel::onAddressChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onCapacityChange = viewModel::onCapacityChange,
        onPriceChange = viewModel::onPriceChange,
        onFreeChange = viewModel::onFreeChange,
        onSave = viewModel::saveChanges,
        onAcceptSuccess = {
            viewModel.clearSaveResult()
            onChangesSaved()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditEventContent(
    state: EditEventUiState,
    onCloseClick: () -> Unit,
    onPhotoClick: () -> Unit,
    onTitleChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onAddressChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCapacityChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onFreeChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onAcceptSuccess: () -> Unit
) {
    when (val result = state.loadResult) {
        RequestResult.Loading -> {
            LoadingIndicator()
            return
        }
        is RequestResult.Failure -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(result.message, color = MaterialTheme.colorScheme.error)
                Button(onClick = onCloseClick) { Text("Volver") }
            }
            return
        }
        RequestResult.Success -> Unit
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editar evento") },
                navigationIcon = {
                    IconButton(onClick = onCloseClick) { Icon(Icons.Default.Close, contentDescription = "Cerrar") }
                }
            )
        },
        bottomBar = {
            Button(
                onClick = onSave,
                enabled = state.saveResult != RequestResult.Loading,
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C6898)),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Guardar cambios") }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                EditPhotoSelector(state.photoUri != null, onPhotoClick)
                EditEventField(state.title, onTitleChange, "Título del evento", "Ej: Torneo de microfútbol", state.titleError)
                EditEventField(state.category, onCategoryChange, "Categoría", "Deportes, Cultura o Comunidad", state.categoryError)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditReadOnlyPicker(state.date, "Fecha", "DD/MM/AAAA", state.dateError, onDateClick, Modifier.weight(1f))
                    EditReadOnlyPicker(state.time, "Hora", "00:00 a.m.", state.timeError, onTimeClick, Modifier.weight(1f))
                }
                EditEventField(state.address, onAddressChange, "Dirección", "Ej: Parque Cafetero", state.addressError)
                EditEventField(state.description, onDescriptionChange, "Descripción (opcional)", "Describe el evento", null, minLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditEventField(
                        state.capacity, onCapacityChange, "Cupo", "Ej: 60", state.capacityError,
                        Modifier.weight(1f), KeyboardType.Number
                    )
                    EditEventField(
                        state.price, onPriceChange, "Precio", if (state.isFree) "Gratis" else "Ej: 5000",
                        state.priceError, Modifier.weight(1f), KeyboardType.Number, enabled = !state.isFree
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Evento gratuito", fontWeight = FontWeight.Bold)
                        Text("Sin costo de inscripción", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(checked = state.isFree, onCheckedChange = onFreeChange)
                }
                (state.saveResult as? RequestResult.Failure)?.let {
                    Text(it.message, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(80.dp))
            }
            if (state.saveResult == RequestResult.Loading) LoadingIndicator()
        }
    }

    if (state.saveResult == RequestResult.Success) {
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(48.dp)) },
            title = { Text("¡Cambios guardados!", fontWeight = FontWeight.Bold) },
            text = { Text("El evento se actualizó temporalmente en este dispositivo.", textAlign = TextAlign.Center) },
            confirmButton = { TextButton(onClick = onAcceptSuccess) { Text("Aceptar") } }
        )
    }
}

@Composable
private fun EditPhotoSelector(selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(100.dp)
            .background(if (selected) Color(0xFFE4F0FF) else Color(0xFFF3F4F6), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (selected) "✓" else "+", fontSize = 24.sp)
            Text(
                if (selected) "Foto seleccionada" else "Cambiar foto de portada (opcional)",
                color = Color(0xFF1E3A8A), fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun EditReadOnlyPicker(
    value: String,
    label: String,
    placeholder: String,
    error: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true, label = { Text(label) },
            placeholder = { Text(placeholder) }, isError = error != null,
            supportingText = error?.let { message -> { Text(message) } }, modifier = Modifier.fillMaxWidth()
        )
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

@Composable
private fun EditEventField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    error: String?,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    minLines: Int = 1,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        placeholder = { Text(placeholder) }, isError = error != null,
        supportingText = error?.let { message -> { Text(message) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType), minLines = minLines,
        enabled = enabled, modifier = modifier.fillMaxWidth()
    )
}

@Preview(showBackground = true, showSystemUi = true, name = "Vista previa - Editar evento")
@Composable
private fun EditEventScreenPreview() {
    AppeventoscomunitariosTheme(darkTheme = false, dynamicColor = false) {
        EditEventContent(
            state = EditEventUiState(
                title = "Torneo de microfútbol",
                category = "Deportes",
                date = "27/09/2026",
                time = "3:00 p.m.",
                address = "Parque Cafetero",
                description = "Evento comunitario",
                capacity = "60",
                isFree = true,
                loadResult = RequestResult.Success
            ),
            onCloseClick = {},
            onPhotoClick = {},
            onTitleChange = {},
            onCategoryChange = {},
            onDateClick = {},
            onTimeClick = {},
            onAddressChange = {},
            onDescriptionChange = {},
            onCapacityChange = {},
            onPriceChange = {},
            onFreeChange = {},
            onSave = {},
            onAcceptSuccess = {}
        )
    }
}
