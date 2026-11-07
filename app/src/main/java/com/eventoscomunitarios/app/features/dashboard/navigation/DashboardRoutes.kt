package com.eventoscomunitarios.app.features.dashboard.navigation

import kotlinx.serialization.Serializable

sealed interface DashboardRoutes {
    @Serializable data class Home(val userId: String) : DashboardRoutes
    @Serializable data class Notifications(val userId: String) : DashboardRoutes
    @Serializable data class Profile(val userId: String) : DashboardRoutes
    @Serializable data class EventDetail(val eventId: Int, val userId: String) : DashboardRoutes
    @Serializable data class CreateEvent(val userId: String, val sourceEventId: Int? = null) : DashboardRoutes
    @Serializable data class EditEvent(val eventId: Int, val userId: String) : DashboardRoutes
    @Serializable data class MyEvents(val userId: String) : DashboardRoutes
    @Serializable data class Map(val userId: String) : DashboardRoutes
    @Serializable data class AdminDashboard(val userId: String) : DashboardRoutes
    @Serializable data class AdminModeration(val userId: String) : DashboardRoutes
    @Serializable data class AdminUsers(val userId: String) : DashboardRoutes
}
