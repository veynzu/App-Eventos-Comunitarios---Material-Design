package com.eventoscomunitarios.app.domain.model

/** Estado de la lectura remota; una colección vacía también puede estar cargando. */
data class RepositorySyncState(
    val isLoading: Boolean = false,
    val error: String? = null
)
