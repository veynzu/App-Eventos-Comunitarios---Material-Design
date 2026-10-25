package com.eventoscomunitarios.app.core.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.eventoscomunitarios.app.R
import com.eventoscomunitarios.app.domain.model.Event
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.extension.compose.annotation.rememberIconImage
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.viewport.data.DefaultViewportTransitionOptions

private val ARMENIA = Point.fromLngLat(-75.6811, 4.5339)
private val locationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

/**
 * Mapa reutilizable de Mapbox. La pantalla conserva la selección y los filtros;
 * este componente se limita a cámara, marcadores y ubicación del dispositivo.
 */
@Composable
fun EventMap(
    events: List<Event>,
    selectedEventId: Int?,
    onEventSelected: (Int) -> Unit,
    onCenterClick: () -> Unit,
    radiusKm: Int,
    modifier: Modifier = Modifier
) {
    if (LocalInspectionMode.current) {
        EventMapPreviewPlaceholder(modifier)
        return
    }

    val context = LocalContext.current
    var hasLocationPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    var followUserRequest by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasLocationPermission = results.values.any { it }
        if (hasLocationPermission) followUserRequest++
    }
    val zoom = if (radiusKm <= 5) 13.5 else 12.5
    val mapViewportState = rememberMapViewportState(key = "radius-$radiusKm") {
        setCameraOptions {
            center(ARMENIA)
            zoom(zoom)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(286.dp)
            .testTag("event_map")
    ) {
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState
        ) {
            val marker = rememberIconImage(
                key = R.drawable.map_marker,
                painter = painterResource(R.drawable.map_marker)
            )

            events.forEach { event ->
                key(event.id) {
                    PointAnnotation(Point.fromLngLat(event.longitude, event.latitude)) {
                        iconImage = marker
                        iconSize = if (event.id == selectedEventId) 1.25 else 1.0
                        interactionsState.onClicked {
                            onEventSelected(event.id)
                            true
                        }
                    }
                }
            }

            if (hasLocationPermission && followUserRequest > 0) {
                MapEffect(followUserRequest) { mapView ->
                    mapView.location.updateSettings {
                        locationPuck = createDefault2DPuck(withBearing = true)
                        enabled = true
                        puckBearing = PuckBearing.COURSE
                        puckBearingEnabled = true
                    }
                    mapViewportState.transitionToFollowPuckState(
                        defaultTransitionOptions = DefaultViewportTransitionOptions.Builder()
                            .maxDurationMs(1500)
                            .build()
                    )
                }
            }
        }

        SmallFloatingActionButton(
            onClick = {
                onCenterClick()
                if (hasLocationPermission) {
                    followUserRequest++
                } else {
                    permissionLauncher.launch(locationPermissions)
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Centrar en mi ubicación",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun Context.hasLocationPermission(): Boolean = locationPermissions.any { permission ->
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun EventMapPreviewPlaceholder(modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().height(286.dp).background(Color(0xFFE7ECE7)),
        contentAlignment = Alignment.Center
    ) {
        Text("Mapa de eventos · Armenia", color = Color(0xFF2B638B))
    }
}
