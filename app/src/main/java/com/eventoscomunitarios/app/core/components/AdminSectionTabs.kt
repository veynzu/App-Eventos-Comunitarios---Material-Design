package com.eventoscomunitarios.app.core.components

import androidx.annotation.StringRes
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.eventoscomunitarios.app.R

enum class AdminDestination(@param:StringRes val label: Int, val testTag: String) {
    DASHBOARD(R.string.nav_admin_dashboard, "admin_nav_dashboard"),
    MODERATION(R.string.nav_admin_moderation, "admin_nav_moderation"),
    USERS(R.string.nav_admin_users, "admin_nav_users")
}

/** Secciones internas del panel; la navegación principal sigue fija en MainScreen. */
@Composable
fun AdminSectionTabs(
    selected: AdminDestination,
    onDashboardClick: () -> Unit,
    onModerationClick: () -> Unit,
    onUsersClick: () -> Unit
) {
    SecondaryTabRow(selectedTabIndex = selected.ordinal) {
        AdminDestination.entries.forEach { destination ->
            Tab(
                selected = destination == selected,
                onClick = when (destination) {
                    AdminDestination.DASHBOARD -> onDashboardClick
                    AdminDestination.MODERATION -> onModerationClick
                    AdminDestination.USERS -> onUsersClick
                },
                modifier = Modifier.testTag(destination.testTag),
                text = { Text(stringResource(destination.label)) }
            )
        }
    }
}
