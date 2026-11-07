package com.eventoscomunitarios.app.core.util

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** El formulario usa texto; el dominio conserva instantes, nunca etiquetas de fecha. */
fun parseEventDateTime(date: String, time: String): Long? {
    if (!date.matches(Regex("\\d{2}/\\d{2}/\\d{4}")) ||
        !time.matches(Regex("\\d{2}:\\d{2}"))) return null
    val text = "$date $time"
    val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ROOT).apply { isLenient = false }
    val position = ParsePosition(0)
    val parsed = formatter.parse(text, position) ?: return null
    return parsed.time.takeIf { position.index == text.length }
}

fun eventFormDate(timestamp: Long): String =
    SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).format(Date(timestamp))

fun eventFormTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(timestamp))
