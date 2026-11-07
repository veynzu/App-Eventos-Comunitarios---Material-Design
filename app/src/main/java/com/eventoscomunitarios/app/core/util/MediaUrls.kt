package com.eventoscomunitarios.app.core.util

import java.net.URI

/** HTTPS en Firebase; HTTP local exclusivamente para Firebase Emulator Suite. */
fun isRemoteImageUri(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.scheme == "https" || (uri.scheme == "http" && uri.host in setOf("10.0.2.2", "localhost", "127.0.0.1"))
}.getOrDefault(false)
