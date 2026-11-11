package com.eventoscomunitarios.app.features.locationpicker
import com.eventoscomunitarios.app.core.util.localizedMessage

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.eventoscomunitarios.app.R
import com.eventoscomunitarios.app.domain.model.Coordinates
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.extension.compose.annotation.rememberIconImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerDialog(
    initial: Coordinates?,
    initialAddress: String,
    onDismiss: () -> Unit,
    onConfirm: (Coordinates, String) -> Unit,
    viewModel: LocationPickerViewModel = hiltViewModel(key = "location-picker")
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.start(initial, initialAddress) }
    DisposableEffect(viewModel) { onDispose { viewModel.cancelRequests() } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.locateDevice() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = Modifier.fillMaxSize().imePadding().testTag("location_picker"),
            topBar = { TopAppBar(title = { Text(stringResource(R.string.choose_location)) },
                navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = { IconButton(onClick = { permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) {
                    Icon(Icons.Default.MyLocation, stringResource(R.string.use_current_location))
                } }) },
            bottomBar = { Button(onClick = { state.point?.let { onConfirm(it, state.address.trim()) } },
                enabled = state.canConfirm, modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("confirm_location")) {
                Text(stringResource(R.string.confirm_location))
            } }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Text(stringResource(R.string.tap_map_location), Modifier.padding(16.dp))
                val token = stringResource(R.string.mapbox_access_token)
                if (token.isBlank() || LocalInspectionMode.current) {
                    Box(Modifier.weight(1f).padding(16.dp)) { Text(stringResource(R.string.map_not_available)) }
                } else {
                    val viewport = rememberMapViewportState { setCameraOptions { center(Point.fromLngLat(state.center.longitude, state.center.latitude)); zoom(15.0) } }
                    LaunchedEffect(state.center) { viewport.setCameraOptions { center(Point.fromLngLat(state.center.longitude, state.center.latitude)); zoom(15.0) } }
                    MapboxMap(Modifier.fillMaxWidth().weight(1f).testTag("location_picker_map"), mapViewportState = viewport,
                        onMapClickListener = { point -> viewModel.selectPoint(Coordinates(point.latitude(), point.longitude())); true }) {
                        val marker = rememberIconImage(key = R.drawable.map_marker, painter = painterResource(R.drawable.map_marker))
                        state.point?.let { point -> PointAnnotation(Point.fromLngLat(point.longitude, point.latitude)) { iconImage = marker } }
                    }
                }
                if (state.isLocating || state.isResolvingAddress) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.notice?.let { Text(localizedMessage(it), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(value = state.address, onValueChange = viewModel::onAddressChange,
                    enabled = state.point != null, modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("location_address"),
                    label = { Text(stringResource(R.string.location_address_reference)) }, maxLines = 3)
            }
        }
    }
}
