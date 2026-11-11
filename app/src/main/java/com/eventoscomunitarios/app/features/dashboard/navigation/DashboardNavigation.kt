package com.eventoscomunitarios.app.features.dashboard.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import com.eventoscomunitarios.app.domain.model.User
import com.eventoscomunitarios.app.domain.model.UserRole
import com.eventoscomunitarios.app.navigation.NavigationTransition
import com.eventoscomunitarios.app.features.map.MapScreen
import com.eventoscomunitarios.app.features.map.MapViewModel
import com.eventoscomunitarios.app.features.admin_dashboard.AdminDashboardScreen
import com.eventoscomunitarios.app.features.admin_dashboard.AdminDashboardViewModel
import com.eventoscomunitarios.app.features.admin_moderation.AdminModerationScreen
import com.eventoscomunitarios.app.features.admin_moderation.AdminModerationViewModel
import com.eventoscomunitarios.app.features.admin_users.AdminUsersScreen
import com.eventoscomunitarios.app.features.admin_users.AdminUsersViewModel
import com.eventoscomunitarios.app.features.home.HomeScreen
import com.eventoscomunitarios.app.features.home.HomeViewModel
import com.eventoscomunitarios.app.features.event_detail.EventDetailScreen
import com.eventoscomunitarios.app.features.event_detail.EventDetailViewModel
import com.eventoscomunitarios.app.features.create_event.CreateEventScreen
import com.eventoscomunitarios.app.features.create_event.CreateEventViewModel
import com.eventoscomunitarios.app.features.edit_event.EditEventScreen
import com.eventoscomunitarios.app.features.edit_event.EditEventViewModel
import com.eventoscomunitarios.app.features.my_events.MyEventsScreen
import com.eventoscomunitarios.app.features.my_events.MyEventsViewModel
import com.eventoscomunitarios.app.features.notifications.NotificationsScreen
import com.eventoscomunitarios.app.features.profile.ProfileScreen

/** Cambiar de sección conserva sus filtros y descarta las rutas de formularios o detalles. */
fun NavHostController.openDashboardSection(destination: DashboardRoutes) {
    while (currentDestination?.let {
        it.hasRoute<DashboardRoutes.CreateEvent>() || it.hasRoute<DashboardRoutes.EditEvent>() ||
            it.hasRoute<DashboardRoutes.EventDetail>() || it.hasRoute<DashboardRoutes.Notifications>()
    } == true) {
        if (!popBackStack()) break
    }
    val start = graph.findStartDestination()
    val returnToStart = destination is DashboardRoutes.AdminDashboard ||
        (destination is DashboardRoutes.Home && start.hasRoute<DashboardRoutes.Home>())
    navigate(destination) {
        popUpTo(start.id) { saveState = true }
        launchSingleTop = true
        restoreState = !returnToStart
    }
}

@Composable
fun DashboardNavigation(
    navController: NavHostController,
    user: User,
    snackbarHostState: SnackbarHostState,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentEntry by navController.currentBackStackEntryAsState()
    fun openSection(destination: DashboardRoutes) {
        navController.openDashboardSection(destination)
    }
    Box(modifier) {
        NavHost(
            navController = navController,
            startDestination = if (user.role == UserRole.ADMINISTRATOR) DashboardRoutes.AdminDashboard(user.id) else DashboardRoutes.Home(user.id),
            enterTransition = { fadeIn(tween(250)) },
            exitTransition = { fadeOut(tween(250)) },
            popEnterTransition = { fadeIn(tween(250)) },
            popExitTransition = { fadeOut(tween(250)) }
        ) {
            composable<DashboardRoutes.Home> { entry ->
                val route = entry.toRoute<DashboardRoutes.Home>()
                val homeViewModel: HomeViewModel = hiltViewModel(entry)

                if (route.userId == user.id) {
                    HomeScreen(
                        userId = route.userId,
                        viewModel = homeViewModel,
                        onAbrirPerfil = { openSection(DashboardRoutes.Profile(route.userId)) },
                        onSessionInvalid = onLogout,
                        onAbrirNotificaciones = { navController.navigate(DashboardRoutes.Notifications(route.userId)) { launchSingleTop = true } },
                        onAbrirMapa = { openSection(DashboardRoutes.Map(route.userId)) },
                        onEventClick = { eventId -> navController.navigate(DashboardRoutes.EventDetail(eventId, route.userId)) }
                    )
                }
            }
            composable<DashboardRoutes.EventDetail> { entry ->
                val route = entry.toRoute<DashboardRoutes.EventDetail>()
                val eventDetailViewModel: EventDetailViewModel = hiltViewModel(entry)

                if (route.userId == user.id) {
                    EventDetailScreen(
                        snackbarHostState = snackbarHostState,
                        viewModel = eventDetailViewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onConfirm = { navController.popBackStack() },
                        onRelaunch = { eventId -> navController.navigate(DashboardRoutes.CreateEvent(route.userId, eventId)) { launchSingleTop = true } }
                    )
                }
            }
            composable<DashboardRoutes.CreateEvent> { entry ->
                val route = entry.toRoute<DashboardRoutes.CreateEvent>()
                if (route.userId == user.id && user.canCreateEvents) {
                    val createEventViewModel: CreateEventViewModel = hiltViewModel(entry)
                    CreateEventScreen(
                        viewModel = createEventViewModel,
                        onCloseClick = { navController.popBackStack() },
                        onEventCreated = { navController.popBackStack() }
                    )
                } else {
                    LaunchedEffect(entry.id) { navController.popBackStack() }
                }
            }
            composable<DashboardRoutes.EditEvent> { entry ->
                val route = entry.toRoute<DashboardRoutes.EditEvent>()
                if (route.userId == user.id && user.canCreateEvents) {
                    val editEventViewModel: EditEventViewModel = hiltViewModel(entry)
                    EditEventScreen(
                        viewModel = editEventViewModel,
                        onCloseClick = { navController.popBackStack() },
                        onChangesSaved = { navController.popBackStack() }
                    )
                } else {
                    LaunchedEffect(entry.id) { navController.popBackStack() }
                }
            }
            composable<DashboardRoutes.MyEvents> { entry ->
                val route = entry.toRoute<DashboardRoutes.MyEvents>()
                val myEventsViewModel: MyEventsViewModel = hiltViewModel(entry)
                if (route.userId == user.id) {
                    MyEventsScreen(
                        snackbarHostState = snackbarHostState,
                        viewModel = myEventsViewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onEventClick = { eventId -> navController.navigate(DashboardRoutes.EventDetail(eventId, route.userId)) },
                        onAdministrarClick = { eventId ->
                            navController.navigate(DashboardRoutes.EditEvent(eventId, route.userId))
                        },
                        onRelaunch = { eventId -> navController.navigate(DashboardRoutes.CreateEvent(route.userId, eventId)) { launchSingleTop = true } }
                    )
                }
            }
            composable<DashboardRoutes.Profile> { entry ->
                val route = entry.toRoute<DashboardRoutes.Profile>()
                if (route.userId == user.id) {
                    ProfileScreen(
                        snackbarHostState = snackbarHostState,
                        userId = route.userId,
                        onNavigateBack = { navController.popBackStack() },
                        onCerrarSesion = onLogout,
                        onNotificationsClick = { navController.navigate(DashboardRoutes.Notifications(route.userId)) { launchSingleTop = true } },
                        onAccountDeleted = onLogout
                    )
                }
            }
            composable<DashboardRoutes.Notifications> { entry ->
                val route = entry.toRoute<DashboardRoutes.Notifications>()
                if (route.userId == user.id) {
                    NotificationsScreen(
                        snackbarHostState = snackbarHostState,
                        onNavigateBack = { navController.popBackStack() },
                        onOpenEvent = { eventId -> navController.navigate(DashboardRoutes.EventDetail(eventId, route.userId)) }
                    )
                }
            }
            composable<DashboardRoutes.Map> { entry ->
                val route = entry.toRoute<DashboardRoutes.Map>()
                val mapViewModel: MapViewModel = hiltViewModel(entry)
                if (route.userId == user.id) {
                    MapScreen(
                        viewModel = mapViewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onEventClick = { eventId -> navController.navigate(DashboardRoutes.EventDetail(eventId, route.userId)) }
                    )
                }
            }
            composable<DashboardRoutes.AdminDashboard> { entry ->
                val route = entry.toRoute<DashboardRoutes.AdminDashboard>()
                val dashboardViewModel: AdminDashboardViewModel = hiltViewModel(entry)
                if (route.userId == user.id && user.role == UserRole.ADMINISTRATOR) {
                    AdminDashboardScreen(
                        viewModel = dashboardViewModel,
                        onNotificationsClick = { navController.navigate(DashboardRoutes.Notifications(route.userId)) { launchSingleTop = true } },
                        onHomeClick = { openSection(DashboardRoutes.Home(route.userId)) },
                        onCreateEventClick = { navController.navigate(DashboardRoutes.CreateEvent(route.userId)) { launchSingleTop = true } },
                        onModerationClick = { openSection(DashboardRoutes.AdminModeration(route.userId)) },
                        onUsersClick = { openSection(DashboardRoutes.AdminUsers(route.userId)) },
                        onEventDetail = { eventId -> navController.navigate(DashboardRoutes.EventDetail(eventId, route.userId)) }
                    )
                }
            }
            composable<DashboardRoutes.AdminModeration> { entry ->
                val route = entry.toRoute<DashboardRoutes.AdminModeration>()
                val moderationViewModel: AdminModerationViewModel = hiltViewModel(entry)
                if (route.userId == user.id && user.role == UserRole.ADMINISTRATOR) {
                    AdminModerationScreen(
                        snackbarHostState = snackbarHostState,
                        viewModel = moderationViewModel,
                        onDashboardClick = { openSection(DashboardRoutes.AdminDashboard(route.userId)) },
                        onUsersClick = { openSection(DashboardRoutes.AdminUsers(route.userId)) },
                        onEventDetail = { eventId -> navController.navigate(DashboardRoutes.EventDetail(eventId, route.userId)) }
                    )
                }
            }
            composable<DashboardRoutes.AdminUsers> { entry ->
                val route = entry.toRoute<DashboardRoutes.AdminUsers>()
                val usersViewModel: AdminUsersViewModel = hiltViewModel(entry)
                if (route.userId == user.id && user.role == UserRole.ADMINISTRATOR) {
                    AdminUsersScreen(
                        snackbarHostState = snackbarHostState,
                        viewModel = usersViewModel,
                        onNotificationsClick = { navController.navigate(DashboardRoutes.Notifications(route.userId)) { launchSingleTop = true } },
                        onDashboardClick = { openSection(DashboardRoutes.AdminDashboard(route.userId)) },
                        onModerationClick = { openSection(DashboardRoutes.AdminModeration(route.userId)) },
                    )
                }
            }
        }
        NavigationTransition(currentEntry)
    }
}
