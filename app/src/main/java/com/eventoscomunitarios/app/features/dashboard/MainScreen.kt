package com.eventoscomunitarios.app.features.dashboard

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.eventoscomunitarios.app.R
import com.eventoscomunitarios.app.core.util.localizedMessage
import com.eventoscomunitarios.app.domain.model.User
import com.eventoscomunitarios.app.features.dashboard.component.BottomNavigationBar
import com.eventoscomunitarios.app.features.dashboard.navigation.DashboardDestination
import com.eventoscomunitarios.app.features.dashboard.navigation.DashboardNavigation
import com.eventoscomunitarios.app.features.dashboard.navigation.DashboardRoutes
import com.eventoscomunitarios.app.features.dashboard.navigation.openDashboardSection

/** Contenedor del dashboard: una barra fija y un NavHost interno para ambos roles. */
@Composable
fun MainScreen(user: User, onLogout: () -> Unit, dataSyncError: String? = null) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val items = DashboardDestination.entries.filter { user.role in it.roles }
    val selected = items.firstOrNull { it.isSelected(currentEntry?.destination) }

    LaunchedEffect(dataSyncError) { dataSyncError?.let { snackbar.showSnackbar(context.localizedMessage(it)) } }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            BottomNavigationBar(items, selected) { destination ->
                navController.openDashboardSection(destination.route(user.id))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (user.canCreateEvents && currentEntry?.destination?.hasRoute<DashboardRoutes.Home>() == true) {
                ExtendedFloatingActionButton(
                    onClick = { navController.navigate(DashboardRoutes.CreateEvent(user.id)) { launchSingleTop = true } },
                    modifier = Modifier.testTag("create_event_button"),
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.nav_create_event)) }
                )
            }
        }
    ) { padding ->
        DashboardNavigation(
            navController = navController,
            user = user,
            snackbarHostState = snackbar,
            onLogout = onLogout,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
        )
    }
}
