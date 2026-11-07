package com.eventoscomunitarios.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavBackStackEntry
import com.eventoscomunitarios.app.core.components.LoadingIndicator

/** Evita retroceder mientras Navigation Compose termina de activar el destino. */
@Composable
fun NavigationTransition(entry: NavBackStackEntry?) {
    var ready by remember(entry?.id) { mutableStateOf(entry?.lifecycle?.currentState == Lifecycle.State.RESUMED) }
    DisposableEffect(entry) {
        val lifecycle = entry?.lifecycle
        val observer = LifecycleEventObserver { _, _ -> ready = lifecycle?.currentState == Lifecycle.State.RESUMED }
        lifecycle?.addObserver(observer)
        ready = lifecycle?.currentState == Lifecycle.State.RESUMED
        onDispose { lifecycle?.removeObserver(observer) }
    }
    if (!ready) LoadingIndicator()
    BackHandler(enabled = !ready) { }
}
