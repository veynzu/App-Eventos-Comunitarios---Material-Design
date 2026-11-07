package com.eventoscomunitarios.app.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.eventoscomunitarios.app.core.components.LoadingIndicator
import com.eventoscomunitarios.app.domain.model.User
import com.eventoscomunitarios.app.features.auth.SessionViewModel
import com.eventoscomunitarios.app.features.dashboard.MainScreen
import com.eventoscomunitarios.app.features.login.LoginScreen
import com.eventoscomunitarios.app.features.onboarding.OnboardingScreen
import com.eventoscomunitarios.app.features.recuperar_contrasenia.RecuperarContrasenaScreen
import com.eventoscomunitarios.app.features.registro.RegistroScreen

/** Grafo principal: acceso público y entrada al dashboard autenticado. */
@Composable
fun AppNavigation(sessionViewModel: SessionViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val session by sessionViewModel.uiState.collectAsState()
    val currentEntry by navController.currentBackStackEntryAsState()

    fun openMain(user: User) {
        navController.navigate(MainRoutes.Main(user.id)) {
            popUpTo(navController.graph.id) { inclusive = false }
            launchSingleTop = true
        }
    }
    val signOut: () -> Unit = {
        sessionViewModel.signOut()
        navController.navigate(MainRoutes.Login) {
            popUpTo(navController.graph.id) { inclusive = false }
            launchSingleTop = true
        }
    }
    val signIn: (User) -> Unit = { user -> sessionViewModel.signIn(user); openMain(user) }

    LaunchedEffect(currentEntry?.id, session.userId, session.isLoading, session.error) {
        if (session.isLoading || session.error != null) return@LaunchedEffect
        val entry = currentEntry ?: return@LaunchedEffect
        val isMain = entry.destination.hasRoute<MainRoutes.Main>()
        val currentUser = session.user
        if (currentUser != null && (!isMain || entry.toRoute<MainRoutes.Main>().userId != currentUser.id)) {
            openMain(currentUser)
        } else if (isMain && session.userId == null) {
            navController.navigate(MainRoutes.Login) {
                popUpTo(navController.graph.id) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            if (session.isLoading) {
                LoadingIndicator()
            } else if (session.error != null && session.userId != null) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(session.error.orEmpty())
                    Button(onClick = sessionViewModel::retry) { Text("Reintentar") }
                    TextButton(onClick = signOut) { Text("Cerrar sesión") }
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
                        Box(Modifier.safeDrawingPadding()) {
                            OnboardingScreen(onContinuar = { navController.navigate(MainRoutes.Login) { launchSingleTop = true } })
                        }
                    }
                    composable<MainRoutes.Login> {
                        LoginScreen(onIniciarSesion = signIn,
                            onCrearCuenta = { navController.navigate(MainRoutes.Register) { launchSingleTop = true } },
                            onRecuperarContrasena = { navController.navigate(MainRoutes.PasswordRecovery) { launchSingleTop = true } })
                    }
                    composable<MainRoutes.Register> {
                        RegistroScreen(onNavigateBack = { navController.popBackStack() }, onRegistroExitoso = signIn)
                    }
                    composable<MainRoutes.PasswordRecovery> {
                        RecuperarContrasenaScreen(onNavigateBack = { navController.popBackStack() })
                    }
                    composable<MainRoutes.Main> { entry ->
                        val user = session.user
                        if (user != null && entry.toRoute<MainRoutes.Main>().userId == user.id) {
                            key(user.id, user.role) { MainScreen(user, signOut, session.dataSyncError) }
                        }
                    }
                }
                NavigationTransition(currentEntry)
            }
        }
    }
}
