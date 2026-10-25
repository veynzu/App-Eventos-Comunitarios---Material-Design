package com.eventoscomunitarios.app.features.edit_event

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventoscomunitarios.app.core.data.FakeEvents
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class EditEventViewModel(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    var titulo by mutableStateOf("")
    var categoria by mutableStateOf("")
    var fecha by mutableStateOf("")
    var hora by mutableStateOf("")
    var direccion by mutableStateOf("")
    var descripcion by mutableStateOf("")
    var cupo by mutableStateOf("")
    var precio by mutableStateOf("")
    var isGratuito by mutableStateOf(true)
    var photoUri by mutableStateOf<Uri?>(null)

    var isLoading by mutableStateOf(false)
        private set
    var showSuccessDialog by mutableStateOf(false)
        private set

    init {
        val eventId: Int? = savedStateHandle["eventId"]
        cargarDatos(eventId)
    }

    private fun cargarDatos(eventId: Int?) {
        val event = FakeEvents.events.firstOrNull { it.id == eventId }
        if (event != null) {
            titulo = event.title
            categoria = event.category.name
            fecha = event.dateLabel
            hora = event.timeLabel
            direccion = event.location
            descripcion = "Descripción pre-cargada del evento."
            cupo = "60"
            precio = if (event.isFree) "" else event.price.toString()
            isGratuito = event.isFree
        }
    }

    fun onGratuitoChanged(isGratis: Boolean) {
        isGratuito = isGratis
        if (isGratis) precio = ""
    }

    fun guardarCambios() {
        isLoading = true
        viewModelScope.launch {
            delay(1500)
            isLoading = false
            showSuccessDialog = true
        }
    }

    fun onDialogDismissed() {
        showSuccessDialog = false
    }
}
