package com.eventoscomunitarios.app.core.components

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eventoscomunitarios.app.R
import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.Coordinates
import com.mapbox.geojson.Point
import com.mapbox.maps.dsl.cameraOptions
import com.mapbox.maps.extension.compose.DisposableMapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.extension.compose.annotation.rememberIconImage
import com.mapbox.maps.plugin.animation.MapAnimationOptions

/**
 * Mapa reutilizable de Mapbox. La pantalla conserva la selección y los filtros;
 * este componente se limita a cámara, marcadores y ubicación del dispositivo.
 */
@Composable
@SuppressLint("ClickableViewAccessibility") // Mapbox procesa los gestos y los clics; el listener solo coordina el scroll.
fun EventMap(
    events: List<Event>,
    selectedEventId: Int?,
    onEventSelected: (Int) -> Unit,
    onCenterClick: () -> Unit,
    radiusKm: Int,
    modifier: Modifier = Modifier,
    center: Coordinates = Coordinates.ARMENIA
) {
    if (LocalInspectionMode.current) {
        EventMapPreviewPlaceholder(modifier)
        return
    }

    if (stringResource(R.string.mapbox_access_token).isBlank()) {
        EventMapConfigurationError(modifier)
        return
    }

    val zoom = (13.5 - kotlin.math.log2(radiusKm.coerceAtLeast(1) / 5.0)).coerceIn(8.0, 16.0)
    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(Point.fromLngLat(center.longitude, center.latitude))
            zoom(zoom)
        }
    }

    var previousTarget by remember { mutableStateOf(center to radiusKm) }
    LaunchedEffect(center, radiusKm) {
        if (previousTarget != (center to radiusKm)) {
            mapViewportState.easeTo(
                cameraOptions {
                    // Ajustar el radio conserva la zona que la persona está explorando.
                    if (previousTarget.first != center) center(Point.fromLngLat(center.longitude, center.latitude))
                    zoom(zoom)
                },
                MapAnimationOptions.mapAnimationOptions { duration(450) }
            )
            previousTarget = center to radiusKm
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
            DisposableMapEffect(Unit) { mapView ->
                // Un arrastre iniciado en el mapa no debe ser interceptado por la lista exterior.
                mapView.setOnTouchListener { view, event ->
                    val touching = event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL
                    view.parent?.requestDisallowInterceptTouchEvent(touching)
                    false
                }
                onDispose {
                    mapView.setOnTouchListener(null)
                    mapView.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }

            val marker = rememberIconImage(
                key = R.drawable.map_marker,
                painter = painterResource(R.drawable.map_marker)
            )

            events.filter { it.coordinates != null }.forEach { event ->
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

        }

        DeviceLocationButton(onCenterClick, Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }
}

@Composable
private fun EventMapPreviewPlaceholder(modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().height(286.dp).background(Color(0xFFE7ECE7)),
        contentAlignment = Alignment.Center
    ) {
        Text(stringResource(R.string.ui_map_title), color = Color(0xFF2B638B))
    }
}

@Composable
private fun EventMapConfigurationError(modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(286.dp)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(24.dp)
            .testTag("event_map"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.ui_map_config),
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}
