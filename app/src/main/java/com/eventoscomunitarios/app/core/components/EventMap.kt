package com.eventoscomunitarios.app.core.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventoscomunitarios.app.domain.model.Event

/** Mapa reutilizable mientras los datos funcionan en memoria y no existe un token de Mapbox. */
@Composable
fun EventMap(
    events: List<Event>,
    selectedEventId: Int?,
    onEventSelected: (Int) -> Unit,
    onCenterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth().height(286.dp).background(Color(0xFFE7ECE7))
            .testTag("event_map")
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0xFFE7ECE7))
            drawRect(
                Color(0xFFCFE3C8),
                topLeft = Offset(size.width * .48f, size.height * .16f),
                size = androidx.compose.ui.geometry.Size(size.width * .34f, size.height * .35f)
            )
            val road = Color.White
            listOf(.12f, .31f, .54f, .76f, .91f).forEach { x ->
                drawLine(road, Offset(size.width * x, 0f), Offset(size.width * (x - .12f), size.height), 13f, StrokeCap.Round)
            }
            listOf(.18f, .43f, .68f, .88f).forEach { y ->
                drawLine(road, Offset(0f, size.height * y), Offset(size.width, size.height * (y + .08f)), 12f, StrokeCap.Round)
            }
            val route = Path().apply {
                moveTo(size.width * .12f, size.height * .74f)
                cubicTo(size.width * .32f, size.height * .55f, size.width * .63f, size.height * .72f, size.width * .88f, size.height * .43f)
            }
            drawPath(route, Color(0xFFB7C7D1), style = Stroke(width = 3f))
            drawCircle(Color(0x332B638B), size.minDimension * .28f, Offset(size.width * .52f, size.height * .57f))
            drawCircle(Color.White, 12f, Offset(size.width * .52f, size.height * .57f))
            drawCircle(Color(0xFF2B638B), 8f, Offset(size.width * .52f, size.height * .57f))
        }
        Text(
            "Parque Cafetero",
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp),
            color = Color(0xFF47704B),
            fontSize = 12.sp
        )
        val alignments = listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.CenterStart, Alignment.CenterEnd)
        events.take(4).forEachIndexed { index, event ->
            Surface(
                modifier = Modifier.align(alignments[index]).padding(
                    start = if (index % 2 == 0) 68.dp else 0.dp,
                    end = if (index % 2 == 1) 64.dp else 0.dp,
                    top = if (index < 2) 76.dp else 0.dp
                ).size(if (event.id == selectedEventId) 42.dp else 36.dp)
                    .clickable { onEventSelected(event.id) }
                    .testTag("map_marker_${event.id}"),
                color = if (event.id == selectedEventId) Color(0xFF163F5D) else Color(0xFF2B638B),
                shape = CircleShape,
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.LocationOn, event.title, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
        IconButton(
            onClick = onCenterClick,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).background(Color.White, CircleShape)
        ) { Icon(Icons.Default.MyLocation, "Centrar en mi ubicación", tint = Color(0xFF2B638B)) }
    }
}
