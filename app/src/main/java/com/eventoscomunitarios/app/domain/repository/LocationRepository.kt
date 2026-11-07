package com.eventoscomunitarios.app.domain.repository

import com.eventoscomunitarios.app.domain.model.Coordinates

interface LocationRepository {
    suspend fun currentLocation(): Coordinates?
    suspend fun addressFor(coordinates: Coordinates): String?
}
