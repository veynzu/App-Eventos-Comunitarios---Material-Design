package com.eventoscomunitarios.app.core.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eventoscomunitarios.app.R
import com.eventoscomunitarios.app.domain.model.EventCategory
import com.eventoscomunitarios.app.domain.model.EventDateField
import com.eventoscomunitarios.app.domain.model.EventSearchOptions
import com.eventoscomunitarios.app.domain.model.EventSortOrder
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/** Stateless search controls reused by event lists and the map. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventSearchControls(
    radiusKm: Float,
    onRadiusChange: (Float) -> Unit,
    options: EventSearchOptions,
    onOptionsChange: (EventSearchOptions) -> Unit,
    category: EventCategory?,
    onCategoryChange: (EventCategory?) -> Unit,
    todayOnly: Boolean,
    onTodayClick: () -> Unit,
    datePickerField: EventDateField?,
    onDatePickerChange: (EventDateField?) -> Unit,
    onDateSelected: (EventDateField, Long) -> Unit,
    onReset: () -> Unit
) {
    val dateFormat = DateFormat.getDateInstance(DateFormat.SHORT, LocalConfiguration.current.locales[0])
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.search_radius, radiusKm.toInt()), style = MaterialTheme.typography.labelLarge)
        Slider(value = radiusKm.coerceIn(1f, 100f), onValueChange = onRadiusChange,
            valueRange = 1f..100f, steps = 98, modifier = Modifier.fillMaxWidth().testTag("event_search_radius"))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item { FilterChip(selected = category == null, onClick = { onCategoryChange(null) },
                label = { Text(stringResource(R.string.search_all_categories)) }) }
            items(EventCategory.entries) { item ->
                FilterChip(selected = category == item, onClick = { onCategoryChange(item) },
                    label = { Text(item.localizedName()) })
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item { FilterChip(selected = todayOnly, onClick = onTodayClick,
                label = { Text(stringResource(R.string.search_today)) }) }
            item { FilterChip(selected = options.freeOnly, onClick = { onOptionsChange(options.copy(freeOnly = !options.freeOnly)) },
                label = { Text(stringResource(R.string.search_free)) }) }
            item { FilterChip(selected = options.availableOnly, onClick = { onOptionsChange(options.copy(availableOnly = !options.availableOnly)) },
                label = { Text(stringResource(R.string.search_available)) }) }
            item { FilterChip(selected = options.outdoorOnly, onClick = { onOptionsChange(options.copy(outdoorOnly = !options.outdoorOnly)) },
                label = { Text(stringResource(R.string.search_outdoor)) }) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onDatePickerChange(EventDateField.FROM) }, modifier = Modifier.weight(1f).testTag("event_date_from")) {
                Text(stringResource(R.string.search_from_date, options.fromDate?.let { dateFormat.format(Date(it)) }
                    ?: stringResource(R.string.search_any_date)))
            }
            OutlinedButton(onClick = { onDatePickerChange(EventDateField.UNTIL) }, modifier = Modifier.weight(1f).testTag("event_date_until")) {
                Text(stringResource(R.string.search_until_date, options.untilDate?.let { dateFormat.format(Date(it)) }
                    ?: stringResource(R.string.search_any_date)))
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item { FilterChip(selected = options.sortOrder == EventSortOrder.DATE,
                onClick = { onOptionsChange(options.copy(sortOrder = EventSortOrder.DATE)) },
                label = { Text(stringResource(R.string.search_sort_date)) }) }
            item { FilterChip(selected = options.sortOrder == EventSortOrder.DISTANCE,
                onClick = { onOptionsChange(options.copy(sortOrder = EventSortOrder.DISTANCE)) },
                label = { Text(stringResource(R.string.search_sort_distance)) }) }
            item { TextButton(onClick = onReset) { Text(stringResource(R.string.search_reset)) } }
        }
    }
    if (datePickerField != null) key(datePickerField) {
        // Material's picker uses UTC dates; preserve the chosen local calendar day.
        val initial = if (datePickerField == EventDateField.FROM) options.fromDate else options.untilDate
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial?.let { localDayToPicker(it) })
        DatePickerDialog(onDismissRequest = { onDatePickerChange(null) },
            confirmButton = { TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { onDateSelected(datePickerField, pickerDayToLocal(it)) }
                onDatePickerChange(null)
            }) { Text(stringResource(R.string.search_apply_date)) } },
            dismissButton = { TextButton(onClick = { onDatePickerChange(null) }) { Text(stringResource(R.string.search_cancel)) } }
        ) { DatePicker(state = picker, title = { Text(stringResource(if (datePickerField == EventDateField.FROM)
            R.string.search_select_from else R.string.search_select_until), Modifier.padding(16.dp)) }) }
    }
}

private fun localDayToPicker(instant: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = instant }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear(); set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun pickerDayToLocal(instant: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = instant }
    return Calendar.getInstance().apply {
        clear(); set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}
