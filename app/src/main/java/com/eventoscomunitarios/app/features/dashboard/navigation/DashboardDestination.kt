package com.eventoscomunitarios.app.features.dashboard.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.eventoscomunitarios.app.R
import com.eventoscomunitarios.app.domain.model.UserRole

/** Las secciones principales y sus roles, siguiendo la guía 11 del profesor. */
enum class DashboardDestination(
    @param:StringRes val label: Int,
    val icon: ImageVector,
    val roles: Set<UserRole>,
    val testTag: String
) {
    HOME(R.string.nav_home, Icons.Default.Home, UserRole.entries.toSet(), "main_nav_home"),
    MAP(R.string.nav_map, Icons.Default.Map, UserRole.entries.toSet(), "main_nav_map"),
    MY_EVENTS(R.string.nav_my_events, Icons.Default.CalendarToday, UserRole.entries.toSet(), "main_nav_events"),
    PROFILE(R.string.nav_profile, Icons.Default.Person, UserRole.entries.toSet(), "main_nav_profile"),
    ADMIN(R.string.nav_admin, Icons.Default.Dashboard, setOf(UserRole.ADMINISTRATOR), "main_nav_admin");

    fun route(userId: String): DashboardRoutes = when (this) {
        HOME -> DashboardRoutes.Home(userId)
        MAP -> DashboardRoutes.Map(userId)
        MY_EVENTS -> DashboardRoutes.MyEvents(userId)
        PROFILE -> DashboardRoutes.Profile(userId)
        ADMIN -> DashboardRoutes.AdminDashboard(userId)
    }

    fun isSelected(destination: NavDestination?): Boolean = when (this) {
        HOME -> destination?.hasRoute<DashboardRoutes.Home>() == true
        MAP -> destination?.hasRoute<DashboardRoutes.Map>() == true
        MY_EVENTS -> destination?.hasRoute<DashboardRoutes.MyEvents>() == true
        PROFILE -> destination?.hasRoute<DashboardRoutes.Profile>() == true
        ADMIN -> destination?.let {
            it.hasRoute<DashboardRoutes.AdminDashboard>() ||
                it.hasRoute<DashboardRoutes.AdminModeration>() || it.hasRoute<DashboardRoutes.AdminUsers>()
        } == true
    }
}
