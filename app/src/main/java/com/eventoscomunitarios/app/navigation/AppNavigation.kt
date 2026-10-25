package com.eventoscomunitarios.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.eventoscomunitarios.app.core.components.LoadingIndicator
import com.eventoscomunitarios.app.domain.model.User
import com.eventoscomunitarios.app.features.auth.SessionViewModel
import com.eventoscomunitarios.app.features.home.HomeScreen
import com.eventoscomunitarios.app.features.home.HomeViewModel
import com.eventoscomunitarios.app.features.event_detail.EventDetailScreen
import com.eventoscomunitarios.app.features.event_detail.EventDetailViewModel
import com.eventoscomunitarios.app.features.create_event.CreateEventScreen
import com.eventoscomunitarios.app.features.edit_event.EditEventScreen
import com.eventoscomunitarios.app.features.edit_event.EditEventViewModel
import com.eventoscomunitarios.app.features.my_events.MyEventsScreen
import com.eventoscomunitarios.app.features.login.LoginScreen
import com.eventoscomunitarios.app.features.onboarding.OnboardingScreen
import com.eventoscomunitarios.app.features.profile.ProfileScreen
import com.eventoscomunitarios.app.features.recuperar_contrasenia.RecuperarContrasenaScreen
import com.eventoscomunitarios.app.features.registro.RegistroScreen
import kotlinx.coroutines.launch

@Composable
fun AppNavigation(sessionViewModel: SessionViewModel = viewModel()) {
    val navController = rememberNavController()
    val session by sessionViewModel.uiState.collectAsState()
    val currentEntry by navController.currentBackStackEntryAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val signOut: () -> Unit = {
        sessionViewModel.signOut()
        navController.navigate(MainRoutes.Login) {
            popUpTo(navController.graph.id) { inclusive = false }
            launchSingleTop = true
        }
    }
    val signIn: (User) -> Unit = { user ->
        sessionViewModel.signIn(user)
        navController.navigate(MainRoutes.Home(user.id)) {
            popUpTo(navController.graph.id) { inclusive = false }
            launchSingleTop = true
        }
    }
    val upcoming: (String) -> Unit = { name ->
        scope.launch { snackbar.showSnackbar("$name estará disponible próximamente") }
    }

    LaunchedEffect(currentEntry?.id, session.userId) {
        val entry = currentEntry ?: return@LaunchedEffect
        val routeUserId = when {
            entry.destination.hasRoute<MainRoutes.Home>() -> entry.toRoute<MainRoutes.Home>().userId
            entry.destination.hasRoute<MainRoutes.Profile>() -> entry.toRoute<MainRoutes.Profile>().userId
            entry.destination.hasRoute<MainRoutes.MyEvents>() -> entry.toRoute<MainRoutes.MyEvents>().userId
            else -> null
        }
        if (routeUserId != null && routeUserId != session.userId) signOut()
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
                    val homeViewModel: HomeViewModel = viewModel(entry)
                    val savedStateHandle = entry.savedStateHandle
                    val confirmedEventId by savedStateHandle.getStateFlow<Int?>("confirmed_event", null).collectAsState()

                    LaunchedEffect(confirmedEventId) {
                        confirmedEventId?.let { id ->
                            homeViewModel.onConfirmEvent(id)
                            savedStateHandle.remove<Int>("confirmed_event")
                        }
                    }

                    if (route.userId == session.userId) {
                        HomeScreen(
                            userId = route.userId,
                            viewModel = homeViewModel,
                            onAbrirPerfil = { navController.navigate(MainRoutes.Profile(route.userId)) { launchSingleTop = true } },
                            onSessionInvalid = signOut,
                            onAbrirMapa = { upcoming("El mapa") },
                            onAbrirMisEventos = { navController.navigate(MainRoutes.MyEvents(route.userId)) { launchSingleTop = true } },
                            onCrearEvento = { navController.navigate(MainRoutes.CreateEvent) { launchSingleTop = true } },
                            onEventClick = { eventId -> navController.navigate(MainRoutes.EventDetail(eventId)) }
                        )
                    }
                }
                composable<MainRoutes.EventDetail> { entry ->
                    val eventDetailViewModel: EventDetailViewModel = viewModel(entry)

                    EventDetailScreen(
                        viewModel = eventDetailViewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onConfirm = { confirmedId ->
                            navController.previousBackStackEntry?.savedStateHandle?.set("confirmed_event", confirmedId)
                            navController.popBackStack()
                        }
                    )
                }
                composable<MainRoutes.CreateEvent> {
                    CreateEventScreen(
                        onCloseClick = { navController.popBackStack() },
                        onEventCreated = { navController.popBackStack() }
                    )
                }
                composable<MainRoutes.EditEvent> { entry ->
                    val editEventViewModel: EditEventViewModel = viewModel(entry)

                    EditEventScreen(
                        viewModel = editEventViewModel,
                        onCloseClick = { navController.popBackStack() },
                        onChangesSaved = { navController.popBackStack() }
                    )
                }
                composable<MainRoutes.MyEvents> { entry ->
                    val route = entry.toRoute<MainRoutes.MyEvents>()
                    if (route.userId == session.userId) {
                        MyEventsScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onAdministrarClick = { eventId -> navController.navigate(MainRoutes.EditEvent(eventId)) }
                        )
                    }
                }
                composable<MainRoutes.Profile> { entry ->
                    val route = entry.toRoute<MainRoutes.Profile>()
                    if (route.userId == session.userId) {
                        ProfileScreen(
                            userId = route.userId,
                            onNavigateBack = { navController.popBackStack() },
                            onCerrarSesion = signOut
                        )
                    }
                }
            }
            if (!destinationReady) LoadingIndicator()
            BackHandler(enabled = !destinationReady) { /* Esperar a que termine la transición. */ }
        }
    }
}