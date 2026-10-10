package com.eventoscomunitarios.app.core.util

sealed interface RequestResult {
    data object Loading : RequestResult
    data object Success : RequestResult
    data class Failure(val message: String) : RequestResult
}
