package com.eventoscomunitarios.app.domain.usecase

import com.eventoscomunitarios.app.domain.model.*
import com.eventoscomunitarios.app.domain.repository.LocationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Inicio y Mapa comparten el mismo punto de búsqueda durante la sesión. */
@Singleton
class LocationUseCases @Inject constructor(private val repository: LocationRepository) {
    private val mutableSearch = MutableStateFlow(SearchLocation())
    val search = mutableSearch.asStateFlow()
    private var initialized = false
    private var generation = 0
    private var sessionUserId: String? = null

    suspend fun initialize(userId: String? = null) {
        if (userId != null && sessionUserId != userId) {
            generation++
            sessionUserId = userId
            initialized = false
            mutableSearch.value = SearchLocation()
        }
        if (initialized) return
        initialized = true
        useDeviceLocation()
    }

    suspend fun useDeviceLocation() {
        val request = ++generation
        mutableSearch.value = mutableSearch.value.copy(isLocating = true, message = null)
        val point = try { repository.currentLocation() }
        catch (cancelled: CancellationException) {
            if (request == generation) { mutableSearch.value = mutableSearch.value.copy(isLocating = false); initialized = false }
            throw cancelled
        }
        catch (_: Exception) { null }
        if (request != generation) return
        mutableSearch.value = if (point?.isValid == true) SearchLocation(point, "Mi ubicación actual", SearchLocationSource.DEVICE)
        else SearchLocation(message = "No se pudo obtener la ubicación. Puedes elegir otro punto; se muestra Armenia.")
    }

    fun select(point: Coordinates, address: String, userId: String? = null) {
        require(point.isValid && address.isNotBlank()) { "Confirma una ubicación válida" }
        generation++
        if (userId != null) sessionUserId = userId
        initialized = true
        mutableSearch.value = SearchLocation(point, address.trim(), SearchLocationSource.SELECTED)
    }
}
