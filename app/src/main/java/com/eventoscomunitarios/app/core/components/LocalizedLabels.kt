package com.eventoscomunitarios.app.core.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.eventoscomunitarios.app.R
import com.eventoscomunitarios.app.domain.model.EventCategory

/** Solo cambia la presentación; los valores persistidos y usados por los filtros se conservan. */
@Composable
fun EventCategory.localizedName(): String = stringResource(when (this) {
    EventCategory.SPORTS -> R.string.category_sports
    EventCategory.CULTURE -> R.string.category_culture
    EventCategory.COMMUNITY -> R.string.category_community
    EventCategory.EDUCATION -> R.string.category_education
    EventCategory.ENVIRONMENT -> R.string.category_environment
    EventCategory.HEALTH -> R.string.category_health
    EventCategory.TECHNOLOGY -> R.string.category_technology
})
