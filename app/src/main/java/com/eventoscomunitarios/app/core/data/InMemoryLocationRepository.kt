package com.eventoscomunitarios.app.core.data

import com.eventoscomunitarios.app.domain.model.Coordinates
import com.eventoscomunitarios.app.domain.repository.LocationRepository

/** Doble aislado para pruebas, sin GPS ni solicitudes de geocodificación reales. */
class InMemoryLocationRepository(var point: Coordinates? = null, var address: String? = null) : LocationRepository {
    override suspend fun currentLocation(): Coordinates? = point
    override suspend fun addressFor(coordinates: Coordinates): String? = address
}
