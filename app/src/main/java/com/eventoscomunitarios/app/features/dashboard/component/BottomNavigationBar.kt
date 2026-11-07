package com.eventoscomunitarios.app.features.dashboard.component

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.eventoscomunitarios.app.features.dashboard.navigation.DashboardDestination

@Composable
fun BottomNavigationBar(
    items: List<DashboardDestination>,
    selected: DashboardDestination?,
    onSelected: (DashboardDestination) -> Unit
) {
    NavigationBar(
        modifier = Modifier.testTag("main_bottom_bar"),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        items.forEach { item ->
            val label = stringResource(item.label)
            NavigationBarItem(
                selected = item == selected,
                onClick = { onSelected(item) },
                modifier = Modifier.testTag(item.testTag),
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(label, maxLines = 1) }
            )
        }
    }
}
