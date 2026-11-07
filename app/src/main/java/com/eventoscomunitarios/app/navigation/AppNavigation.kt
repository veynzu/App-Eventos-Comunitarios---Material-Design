package com.eventoscomunitarios.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.eventoscomunitarios.app.core.components.LoadingIndicator
import com.eventoscomunitarios.app.domain.model.User
import com.eventoscomunitarios.app.domain.model.UserRole
import com.eventoscomunitarios.app.features.map.MapScreen
import com.eventoscomunitarios.app.features.map.MapViewModel
import com.eventoscomunitarios.app.features.admin_dashboard.AdminDashboardScreen
import com.eventoscomunitarios.app.features.admin_dashboard.AdminDashboardViewModel
import com.eventoscomunitarios.app.features.admin_moderation.AdminModerationScreen
import com.eventoscomunitarios.app.features.admin_moderation.AdminModerationViewModel
import com.eventoscomunitarios.app.features.admin_users.AdminUsersScreen
import com.eventoscomunitarios.app.features.admin_users.AdminUsersViewModel
import com.eventoscomunitarios.app.features.auth.SessionViewModel
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
import com.eventoscomunitarios.app.features.login.LoginScreen
import com.eventoscomunitarios.app.features.onboarding.OnboardingScreen
import com.eventoscomunitarios.app.features.notifications.NotificationsScreen
import com.eventoscomunitarios.app.features.profile.ProfileScreen
import com.eventoscomunitarios.app.features.recuperar_contrasenia.RecuperarContrasenaScreen
import com.eventoscomunitarios.app.features.registro.RegistroScreen

@Composable
fun AppNavigation(sessionViewModel: SessionViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val session by sessionViewModel.uiState.collectAsState()
    val currentEntry by navController.currentBackStackEntryAsState()
    val snackbar = remember { SnackbarHostState() }

    fun openSection(destination: MainRoutes) {
        if (!navController.popBackStack(destination, inclusive = false)) {
            navController.navigate(destination) { launchSingleTop = true }
        }
    }

    val signOut: () -> Unit = {
        sessionViewModel.signOut()
        navController.navigate(MainRoutes.Login) {
            popUpTo(navController.graph.id) { inclusive = false }
            launchSingleTop = true
        }
    }
    val signIn: (User) -> Unit = { user ->
        sessionViewModel.signIn(user)
        val destination = if (user.role == UserRole.ADMINISTRATOR) {
            MainRoutes.AdminDashboard(user.id)
        } else {
            MainRoutes.Home(user.id)
        }
        navController.navigate(destination) {
            popUpTo(navController.graph.id) { inclusive = false }
            launchSingleTop = true
        }
    }
    LaunchedEffect(session.dataSyncError) {
        session.dataSyncError?.let { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(currentEntry?.id, session.userId, session.isLoading, session.error, session.user?.role) {
        if (session.isLoading || session.error != null) return@LaunchedEffect
        val entry = currentEntry ?: return@LaunchedEffect
        val routeUserId = when {
            entry.destination.hasRoute<MainRoutes.Home>() -> entry.toRoute<MainRoutes.Home>().userId
            entry.destination.hasRoute<MainRoutes.Notifications>() -> entry.toRoute<MainRoutes.Notifications>().userId
            entry.destination.hasRoute<MainRoutes.Profile>() -> entry.toRoute<MainRoutes.Profile>().userId
            entry.destination.hasRoute<MainRoutes.MyEvents>() -> entry.toRoute<MainRoutes.MyEvents>().userId
            entry.destination.hasRoute<MainRoutes.EventDetail>() -> entry.toRoute<MainRoutes.EventDetail>().userId
            entry.destination.hasRoute<MainRoutes.CreateEvent>() -> entry.toRoute<MainRoutes.CreateEvent>().userId
            entry.destination.hasRoute<MainRoutes.EditEvent>() -> entry.toRoute<MainRoutes.EditEvent>().userId
            entry.destination.hasRoute<MainRoutes.Map>() -> entry.toRoute<MainRoutes.Map>().userId
            entry.destination.hasRoute<MainRoutes.AdminDashboard>() -> entry.toRoute<MainRoutes.AdminDashboard>().userId
            entry.destination.hasRoute<MainRoutes.AdminModeration>() -> entry.toRoute<MainRoutes.AdminModeration>().userId
            entry.destination.hasRoute<MainRoutes.AdminUsers>() -> entry.toRoute<MainRoutes.AdminUsers>().userId
            else -> null
        }
        val currentUser = session.user
        val isPublicRoute = entry.destination.hasRoute<MainRoutes.Onboarding>() ||
            entry.destination.hasRoute<MainRoutes.Login>() || entry.destination.hasRoute<MainRoutes.Register>() ||
            entry.destination.hasRoute<MainRoutes.PasswordRecovery>()
        if (currentUser != null && (isPublicRoute || (routeUserId != null && routeUserId != currentUser.id))) {
            val destination = if (currentUser.role == UserRole.ADMINISTRATOR) MainRoutes.AdminDashboard(currentUser.id) else MainRoutes.Home(currentUser.id)
            navController.navigate(destination) { popUpTo(navController.graph.id) { inclusive = false }; launchSingleTop = true }
        } else if (routeUserId != null && session.userId == null) {
            navController.navigate(MainRoutes.Login) { popUpTo(navController.graph.id) { inclusive = false }; launchSingleTop = true }
        }
    }

    var destinationReady by remember(currentEntry?.id) {
        mutableStateOf(currentEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED)
    }
    DisposableEffect(currentEntry) {
        val lifecycle = currentEntry?.lifecycle
        val observer = LifecycleEventObserver { _, _ ->
            destinationReady = lifecycle?.currentState == Lifecycle.State.RESUMED
        }
        lifecycle?.addObserver(observer)
        destinationReady = lifecycle?.currentState == Lifecycle.State.RESUMED
        onDispose { lifecycle?.removeObserver(observer) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (session.isLoading) {
                LoadingIndicator()
            } else if (session.error != null && session.userId != null) {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Text(session.error.orEmpty())
                    Button(onClick = sessionViewModel::retry) { Text("Reintentar") }
                    TextButton(onClick = sessionViewModel::signOut) { Text("Cerrar sesión") }
                }
            } else {
            NavHost(
                navController = navController,
                startDestination = MainRoutes.Onboarding,
                enterTransition = { fadeIn(tween(250)) },
                exitTransition = { fadeOut(tween(250)) },
                popEnterTransition = { fadeIn(tween(250)) },
                popExitTransition = { fadeOut(tween(250)) }
            ) {
                composable<MainRoutes.Onboarding> {
                    OnboardingScreen(onContinuar = { navController.navigate(MainRoutes.Login) { launchSingleTop = true } })
                }
                composable<MainRoutes.Login> {
                    LoginScreen(
                        onIniciarSesion = signIn,
                        onCrearCuenta = { navController.navigate(MainRoutes.Register) { launchSingleTop = true } },
                        onRecuperarContrasena = { navController.navigate(MainRoutes.PasswordRecovery) { launchSingleTop = true } }
                    )
                }
                composable<MainRoutes.Register> {
                    RegistroScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onRegistroExitoso = signIn
                    )
                }
                composable<MainRoutes.PasswordRecovery> {
                    RecuperarContrasenaScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable<MainRoutes.Home> { entry ->
                    val route = entry.toRoute<MainRoutes.Home>()
                    val homeViewModel: HomeViewModel = hiltViewModel(entry)

                    if (route.userId == session.userId) {
                        HomeScreen(
                            userId = route.userId,
                            viewModel = homeViewModel,
                            onAbrirPerfil = { openSection(MainRoutes.Profile(route.userId)) },
                            onSessionInvalid = signOut,
                            onAbrirNotificaciones = { navController.navigate(MainRoutes.Notifications(route.userId)) { launchSingleTop = true } },
                            onVolverModeracion = { openSection(MainRoutes.AdminDashboard(route.userId)) },
                            onAbrirMapa = { openSection(MainRoutes.Map(route.userId)) },
                            onAbrirMisEventos = { openSection(MainRoutes.MyEvents(route.userId)) },
                            onCrearEvento = { navController.navigate(MainRoutes.CreateEvent(route.userId)) { launchSingleTop = true } },
                            onEventClick = { eventId -> navController.navigate(MainRoutes.EventDetail(eventId, route.userId)) }
                        )
                    }
                }
                composable<MainRoutes.EventDetail> { entry ->
                    val route = entry.toRoute<MainRoutes.EventDetail>()
                    val eventDetailViewModel: EventDetailViewModel = hiltViewModel(entry)

                    if (route.userId == session.userId) {
                        EventDetailScreen(
                            viewModel = eventDetailViewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onConfirm = { navController.popBackStack() }
                        )
                    }
                }
                composable<MainRoutes.CreateEvent> { entry ->
                    val route = entry.toRoute<MainRoutes.CreateEvent>()
                    val createEventViewModel: CreateEventViewModel = hiltViewModel(entry)
                    if (route.userId == session.userId) {
                        CreateEventScreen(
                            viewModel = createEventViewModel,
                            onCloseClick = { navController.popBackStack() },
                            onEventCreated = { navController.popBackStack() }
                        )
                    }
                }
                composable<MainRoutes.EditEvent> { entry ->
                    val route = entry.toRoute<MainRoutes.EditEvent>()
                    val editEventViewModel: EditEventViewModel = hiltViewModel(entry)

                    if (route.userId == session.userId) {
                        EditEventScreen(
                            viewModel = editEventViewModel,
                            onCloseClick = { navController.popBackStack() },
                            onChangesSaved = { navController.popBackStack() }
                        )
                    }
                }
                composable<MainRoutes.MyEvents> { entry ->
                    val route = entry.toRoute<MainRoutes.MyEvents>()
                    val myEventsViewModel: MyEventsViewModel = hiltViewModel(entry)
                    if (route.userId == session.userId) {
                        MyEventsScreen(
                            viewModel = myEventsViewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onEventClick = { eventId -> navController.navigate(MainRoutes.EventDetail(eventId, route.userId)) },
                            onAdministrarClick = { eventId ->
                                navController.navigate(MainRoutes.EditEvent(eventId, route.userId))
                            }
                        )
                    }
                }
                composable<MainRoutes.Profile> { entry ->
                    val route = entry.toRoute<MainRoutes.Profile>()
                    if (route.userId == session.userId) {
                        ProfileScreen(
                            userId = route.userId,
                            onNavigateBack = { navController.popBackStack() },
                            onCerrarSesion = signOut,
                            onNotificationsClick = { navController.navigate(MainRoutes.Notifications(route.userId)) { launchSingleTop = true } },
                            onAccountDeleted = signOut
                        )
                    }
                }
                composable<MainRoutes.Notifications> { entry ->
                    val route = entry.toRoute<MainRoutes.Notifications>()
                    if (route.userId == session.userId) {
                        NotificationsScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onOpenEvent = { eventId -> navController.navigate(MainRoutes.EventDetail(eventId, route.userId)) }
                        )
                    }
                }
                composable<MainRoutes.Map> { entry ->
                    val route = entry.toRoute<MainRoutes.Map>()
                    val mapViewModel: MapViewModel = hiltViewModel(entry)
                    if (route.userId == session.userId) {
                        MapScreen(
                            viewModel = mapViewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onHomeClick = { openSection(MainRoutes.Home(route.userId)) },
                            onMyEventsClick = { openSection(MainRoutes.MyEvents(route.userId)) },
                            onProfileClick = { openSection(MainRoutes.Profile(route.userId)) },
                            onEventClick = { eventId -> navController.navigate(MainRoutes.EventDetail(eventId, route.userId)) }
                        )
                    }
                }
                composable<MainRoutes.AdminDashboard> { entry ->
                    val route = entry.toRoute<MainRoutes.AdminDashboard>()
                    val dashboardViewModel: AdminDashboardViewModel = hiltViewModel(entry)
                    if (route.userId == session.userId) {
                        AdminDashboardScreen(
                            viewModel = dashboardViewModel,
                            onNotificationsClick = { navController.navigate(MainRoutes.Notifications(route.userId)) { launchSingleTop = true } },
                            onHomeClick = { openSection(MainRoutes.Home(route.userId)) },
                            onCreateEventClick = { navController.navigate(MainRoutes.CreateEvent(route.userId)) { launchSingleTop = true } },
                            onModerationClick = { openSection(MainRoutes.AdminModeration(route.userId)) },
                            onUsersClick = { openSection(MainRoutes.AdminUsers(route.userId)) },
                            onProfileClick = { openSection(MainRoutes.Profile(route.userId)) },
                            onEventDetail = { eventId -> navController.navigate(MainRoutes.EventDetail(eventId, route.userId)) }
                        )
                    }
                }
                composable<MainRoutes.AdminModeration> { entry ->
                    val route = entry.toRoute<MainRoutes.AdminModeration>()
                    val moderationViewModel: AdminModerationViewModel = hiltViewModel(entry)
                    if (route.userId == session.userId) {
                        AdminModerationScreen(
                            viewModel = moderationViewModel,
                            onDashboardClick = { openSection(MainRoutes.AdminDashboard(route.userId)) },
                            onUsersClick = { openSection(MainRoutes.AdminUsers(route.userId)) },
                            onProfileClick = { openSection(MainRoutes.Profile(route.userId)) },
                            onEventDetail = { eventId -> navController.navigate(MainRoutes.EventDetail(eventId, route.userId)) }
                        )
                    }
                }
                composable<MainRoutes.AdminUsers> { entry ->
                    val route = entry.toRoute<MainRoutes.AdminUsers>()
                    val usersViewModel: AdminUsersViewModel = hiltViewModel(entry)
                    if (route.userId == session.userId) {
                        AdminUsersScreen(
                            viewModel = usersViewModel,
                            onNotificationsClick = { navController.navigate(MainRoutes.Notifications(route.userId)) { launchSingleTop = true } },
                            onDashboardClick = { openSection(MainRoutes.AdminDashboard(route.userId)) },
                            onModerationClick = { openSection(MainRoutes.AdminModeration(route.userId)) },
                            onProfileClick = { openSection(MainRoutes.Profile(route.userId)) }
                        )
                    }
                }
            }
            if (!destinationReady) LoadingIndicator()
            BackHandler(enabled = !destinationReady) { /* Esperar a que termine la transición. */ }
            }
        }
    }
}
