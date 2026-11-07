package com.eventoscomunitarios.app.core.data

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

/** El SDK normaliza URL de producción; las URL del emulador usan su ruta de objeto. */
fun FirebaseStorage.imageReference(url: String): StorageReference {
    val uri = Uri.parse(url)
    if (uri.scheme == "http" && uri.host in setOf("10.0.2.2", "localhost", "127.0.0.1")) {
        val parts = uri.pathSegments
        require(parts.size == 5 && parts[0] == "v0" && parts[1] == "b" && parts[3] == "o" &&
            parts[2] == reference.bucket && parts[4].isNotBlank()) {
            "La dirección de la imagen no pertenece al almacenamiento de la aplicación."
        }
        return reference.child(parts[4])
    }
    return getReferenceFromUrl(url)
}
