package com.eventoscomunitarios.app.core.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eventoscomunitarios.app.R
import com.eventoscomunitarios.app.core.util.localizedMessage
import com.eventoscomunitarios.app.domain.model.SearchLocation

@Composable
fun DeviceLocationButton(onLocate: () -> Unit, modifier: Modifier = Modifier) {
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onLocate() }
    SmallFloatingActionButton(onClick = { permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }, modifier = modifier) {
        Icon(Icons.Default.MyLocation, stringResource(R.string.use_current_location))
    }
}

@Composable
fun SearchLocationControls(location: SearchLocation, onChoose: () -> Unit, onLocate: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(location.address, style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = onChoose) { Text(stringResource(R.string.search_in_another_area)) }
            DeviceLocationButton(onLocate)
        }
        if (location.isLocating) LinearProgressIndicator(Modifier.fillMaxWidth())
        location.message?.let { Text(localizedMessage(it), style = MaterialTheme.typography.bodySmall) }
    }
}
