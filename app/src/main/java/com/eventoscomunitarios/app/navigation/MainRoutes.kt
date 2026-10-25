package com.eventoscomunitarios.app.navigation

import kotlinx.serialization.Serializable

sealed interface MainRoutes {
    @Serializable data object Onboarding : MainRoutes
    @Serializable data object Login : MainRoutes
    @Serializable data object Register : MainRoutes
    @Serializable data object PasswordRecovery : MainRoutes
    @Serializable data class Home(val userId: String) : MainRoutes
    @Serializable data class Profile(val userId: String) : MainRoutes
    @Serializable data class EventDetail(val eventId: Int, val userId: String) : MainRoutes
    @Serializable data class CreateEvent(val userId: String) : MainRoutes
    @Serializable data class EditEvent(val eventId: Int, val userId: String) : MainRoutes
    @Serializable data class MyEvents(val userId: String) : MainRoutes
}
