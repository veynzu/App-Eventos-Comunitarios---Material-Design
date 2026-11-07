package com.eventoscomunitarios.app.domain.model

import kotlin.math.*

data class Coordinates(val latitude: Double, val longitude: Double) {
    val isValid: Boolean get() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

    fun distanceKmTo(other: Coordinates): Double {
        require(isValid && other.isValid) { "Coordenadas inválidas" }
        val lat = Math.toRadians(other.latitude - latitude)
        val lon = Math.toRadians(other.longitude - longitude)
        val a = sin(lat / 2).pow(2) + cos(Math.toRadians(latitude)) * cos(Math.toRadians(other.latitude)) * sin(lon / 2).pow(2)
        return 6371.0088 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    companion object { val ARMENIA = Coordinates(4.5339, -75.6811) }
}

enum class SearchLocationSource { FALLBACK, DEVICE, SELECTED }

data class SearchLocation(
    val coordinates: Coordinates = Coordinates.ARMENIA,
    val address: String = "Armenia, Quindío",
    val source: SearchLocationSource = SearchLocationSource.FALLBACK,
    val isLocating: Boolean = false,
    val message: String? = null
)
