package com.eventoscomunitarios.app.core.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

enum class AdminDestination { DASHBOARD, MODERATION, USERS, PROFILE }

@Composable
fun AdminBottomBar(
    selected: AdminDestination,
    onDashboardClick: () -> Unit,
    onModerationClick: () -> Unit,
    onUsersClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    NavigationBar(containerColor = Color.White) {
        NavigationBarItem(
            selected = selected == AdminDestination.DASHBOARD,
            onClick = onDashboardClick,
            icon = { Icon(Icons.Default.Dashboard, "Panel") },
            modifier = Modifier.testTag("admin_nav_dashboard"),
            label = { Text("Panel") }
        )
        NavigationBarItem(
            selected = selected == AdminDestination.MODERATION,
            onClick = onModerationClick,
            icon = { Icon(Icons.Default.FactCheck, "Moderación") },
            modifier = Modifier.testTag("admin_nav_moderation"),
            label = { Text("Moderación") }
        )
        NavigationBarItem(
            selected = selected == AdminDestination.USERS,
            onClick = onUsersClick,
            icon = { Icon(Icons.Default.People, "Usuarios") },
            modifier = Modifier.testTag("admin_nav_users"),
            label = { Text("Usuarios") }
        )
        NavigationBarItem(
            selected = selected == AdminDestination.PROFILE,
            onClick = onProfileClick,
            icon = { Icon(Icons.Default.Person, "Perfil") },
            modifier = Modifier.testTag("admin_nav_profile"),
            label = { Text("Perfil") }
        )
    }
}
