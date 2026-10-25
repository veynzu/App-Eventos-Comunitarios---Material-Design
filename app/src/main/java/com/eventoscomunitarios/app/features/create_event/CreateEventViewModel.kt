package com.eventoscomunitarios.app.features.create_event

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CreateEventViewModel : ViewModel() {

    // Campos del formulario inicializados vacíos
    var titulo by mutableStateOf("")
    var categoria by mutableStateOf("")
    var fecha by mutableStateOf("")
    var hora by mutableStateOf("")
    var direccion by mutableStateOf("")
    var descripcion by mutableStateOf("")
    var cupo by mutableStateOf("")
    var precio by mutableStateOf("")
    var isGratuito by mutableStateOf(true)

    // Estado para la foto de portada
    var photoUri by mutableStateOf<Uri?>(null)

    // Estados de UI
    var isLoading by mutableStateOf(false)
        private set
    var showSuccessDialog by mutableStateOf(false)
        private set

    fun onGratuitoChanged(isGratis: Boolean) {
        isGratuito = isGratis
        if (isGratis) precio = ""
    }

    fun publicarEvento() {
        isLoading = true
        viewModelScope.launch {
            // Simulación de guardado
            delay(1500)
            isLoading = false
            showSuccessDialog = true
        }
    }

    fun onDialogDismissed() {
        showSuccessDialog = false
    }
}