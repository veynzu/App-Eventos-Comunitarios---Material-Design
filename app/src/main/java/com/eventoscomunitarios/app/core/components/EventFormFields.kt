package com.eventoscomunitarios.app.core.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.eventoscomunitarios.app.core.util.parseEventDateTime
import com.eventoscomunitarios.app.domain.model.EventCategory
import java.util.Calendar
import java.util.Locale

@Composable
fun EventTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String? = null,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    minLines: Int = 1,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { message -> { Text(message) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
        minLines = minLines, singleLine = minLines == 1, enabled = enabled,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun EventCategorySelector(value: String, error: String?, onSelect: (String) -> Unit) {
    Column {
        Text("Categoría", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EventCategory.entries.forEach { category ->
                FilterChip(
                    selected = value.equals(category.displayName, true) || value.equals(category.name, true),
                    onClick = { onSelect(category.displayName) }, label = { Text(category.displayName) }
                )
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun EventDateTimeFields(
    label: String,
    date: String,
    time: String,
    dateError: String?,
    timeError: String?,
    onDateChange: (String) -> Unit,
    onTimeChange: (String) -> Unit
) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance().apply {
        parseEventDateTime(date, time.ifBlank { "12:00" })?.let { timeInMillis = it }
    }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EventPickerField(date, "Fecha (DD/MM/AAAA)", dateError, Modifier.weight(1f)) {
                DatePickerDialog(context, { _, year, month, day ->
                    onDateChange(String.format(Locale.ROOT, "%02d/%02d/%04d", day, month + 1, year))
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
            }
            EventPickerField(time, "Hora (24 h)", timeError, Modifier.weight(1f)) {
                TimePickerDialog(context, { _, hour, minute ->
                    onTimeChange(String.format(Locale.ROOT, "%02d:%02d", hour, minute))
                }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
            }
        }
    }
}

@Composable
private fun EventPickerField(value: String, label: String, error: String?, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true,
            label = { Text(label) }, isError = error != null,
            supportingText = error?.let { message -> { Text(message) } },
            modifier = Modifier.fillMaxWidth()
        )
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

@Composable
fun EventPhotoSelector(photoUri: String?, onPhotoSelected: (String?) -> Unit) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) { /* Algunos proveedores solo conceden acceso temporal. */ }
            onPhotoSelected(uri.toString())
        }
    }
    if (!photoUri.isNullOrBlank()) {
        RemoteImage(photoUri, "Foto seleccionada", Modifier.fillMaxWidth().height(180.dp))
        TextButton(onClick = { onPhotoSelected(null) }) { Text("Quitar foto") }
    }
    OutlinedButton(
        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) { Text(if (photoUri == null) "Agregar foto (opcional)" else "Cambiar foto seleccionada") }
    Text("Imagen de hasta 10 MB", style = MaterialTheme.typography.bodySmall)
}
