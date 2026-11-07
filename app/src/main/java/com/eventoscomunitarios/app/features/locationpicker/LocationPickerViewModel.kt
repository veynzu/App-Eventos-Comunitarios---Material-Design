package com.eventoscomunitarios.app.features.locationpicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventoscomunitarios.app.domain.model.Coordinates
import com.eventoscomunitarios.app.domain.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class LocationPickerUiState(
    val center: Coordinates = Coordinates.ARMENIA,
    val point: Coordinates? = null,
    val address: String = "",
    val isLocating: Boolean = false,
    val isResolvingAddress: Boolean = false,
    val notice: String? = null
) {
    val canConfirm: Boolean get() = point?.isValid == true && address.isNotBlank() && !isResolvingAddress
}

@HiltViewModel
class LocationPickerViewModel @Inject constructor(private val locations: LocationRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(LocationPickerUiState())
    val uiState = mutableState.asStateFlow()
    private var addressJob: Job? = null
    private var deviceJob: Job? = null
    private var generation = 0
    private var addressRevision = 0

    fun start(initial: Coordinates?, address: String) {
        cancelRequests()
        mutableState.value = LocationPickerUiState(center = initial ?: Coordinates.ARMENIA, point = initial, address = address)
        if (initial == null) locateDevice()
    }

    fun locateDevice() {
        deviceJob?.cancel()
        val request = generation
        mutableState.update { it.copy(isLocating = true) }
        deviceJob = viewModelScope.launch {
            val point = try { locations.currentLocation()?.takeIf { it.isValid } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            if (request != generation) return@launch
            mutableState.update { it.copy(center = point ?: Coordinates.ARMENIA, isLocating = false,
                notice = if (point == null) "No se pudo obtener la ubicación actual. Se muestra Armenia; toca el mapa para elegir el sitio." else null) }
        }
    }

    fun selectPoint(point: Coordinates) {
        if (!point.isValid) return
        deviceJob?.cancel()
        addressJob?.cancel()
        val request = ++generation
        val revision = ++addressRevision
        mutableState.update { it.copy(point = point, address = "", isResolvingAddress = true, isLocating = false, notice = null) }
        addressJob = viewModelScope.launch {
            val address = try { withTimeoutOrNull(6_000) { locations.addressFor(point) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            if (request != generation) return@launch
            mutableState.update { it.copy(
                address = if (revision == addressRevision) address.orEmpty() else it.address,
                isResolvingAddress = false,
                notice = if (address.isNullOrBlank()) "No se encontró una dirección. Escribe la dirección o referencia del punto seleccionado." else null
            ) }
        }
    }

    fun onAddressChange(value: String) {
        addressRevision++
        mutableState.update { it.copy(address = value.take(250)) }
    }

    fun cancelRequests() {
        generation++
        addressJob?.cancel()
        deviceJob?.cancel()
    }
}
