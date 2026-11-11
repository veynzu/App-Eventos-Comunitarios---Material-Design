package com.eventoscomunitarios.app.core.util

import androidx.compose.runtime.Composable
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import com.eventoscomunitarios.app.R
import com.eventoscomunitarios.app.domain.model.AppNotification

/** Compatibilidad con avisos ya guardados como texto por el contrato actual de Firestore. */
@Composable
fun localizedNotificationMessage(notification: AppNotification): String = LocalContext.current.localizedNotificationMessage(notification)

fun Context.localizedNotificationMessage(notification: AppNotification): String {
    // En estos avisos el cuerpo completo proviene del autor y no se traduce.
    if (notification.title in setOf("Publicación por revisar", "Publicación actualizada por revisar", "Publicación rechazada")) return notification.message
    val template = notificationTemplates.firstOrNull { it.title == notification.title && it.pattern.matches(notification.message) }
        ?: return if (notification.title in setOf("Bienvenido a Share You", "Estado de cuenta actualizado", "Comentario retirado", "Verificación de identidad"))
            localizedMessage(notification.message) else notification.message
    val arguments = template.pattern.matchEntire(notification.message)!!.groupValues.drop(1).toTypedArray()
    return getString(template.resource, *arguments)
}

private data class NotificationTemplate(val title: String, val pattern: Regex, val resource: Int)
private val notificationTemplates = listOf(
    NotificationTemplate("Publicación enviada", Regex("(.*) está pendiente de revisión\\.", RegexOption.DOT_MATCHES_ALL), R.string.notification_pending_message),
    NotificationTemplate("Publicación aprobada", Regex("(.*) ya es visible para la comunidad\\.", RegexOption.DOT_MATCHES_ALL), R.string.notification_approved_message),
    NotificationTemplate("Evento actualizado", Regex("(.*) cambió\\. Revisa las fechas; está pendiente de nueva aprobación\\.", RegexOption.DOT_MATCHES_ALL), R.string.notification_updated_message),
    NotificationTemplate("Evento aprobado", Regex("(.*) está aprobado\\. Tu inscripción se conserva\\.", RegexOption.DOT_MATCHES_ALL), R.string.notification_registration_kept_message),
    NotificationTemplate("Evento rechazado", Regex("(.*) fue rechazado\\. Tu inscripción se canceló\\. Motivo: (.*)", RegexOption.DOT_MATCHES_ALL), R.string.notification_rejected_message),
    NotificationTemplate("Evento finalizado", Regex("(.*) ha finalizado\\. Puedes compartir tu comentario y valoración\\.", RegexOption.DOT_MATCHES_ALL), R.string.notification_finished_message),
    NotificationTemplate("Asistencia confirmada", Regex("Tienes un cupo para (.*)\\.", RegexOption.DOT_MATCHES_ALL), R.string.notification_confirmed_message),
    NotificationTemplate("Estás en lista de espera", Regex("Te avisaremos si se libera un cupo para (.*)\\.", RegexOption.DOT_MATCHES_ALL), R.string.notification_waiting_message),
    NotificationTemplate("Inscripción cancelada", Regex("Cancelaste tu inscripción a (.*)\\.", RegexOption.DOT_MATCHES_ALL), R.string.notification_cancelled_message),
    NotificationTemplate("Publicación eliminada", Regex("(.*) fue eliminada; su inscripción quedó cancelada\\.", RegexOption.DOT_MATCHES_ALL), R.string.notification_deleted_registration_message),
    NotificationTemplate("Publicación eliminada", Regex("(.*) fue eliminada\\.", RegexOption.DOT_MATCHES_ALL), R.string.notification_deleted_message),
    NotificationTemplate("¡Se liberó un cupo!", Regex("Tu asistencia a (.*) quedó confirmada desde la lista de espera\\.", RegexOption.DOT_MATCHES_ALL), R.string.notification_promoted_message),
    NotificationTemplate("Nuevo comentario", Regex("(.*) comentó en (.*)\\.", RegexOption.DOT_MATCHES_ALL), R.string.notification_comment_message),
    NotificationTemplate("Nueva edición de tu evento favorito", Regex("(.*): (.*) a las (.*)\\. Confirma si participarás\\.", RegexOption.DOT_MATCHES_ALL), R.string.notification_followed_message),
    NotificationTemplate("Nuevo evento de alguien que sigues", Regex("(.*): (.*) a las (.*)\\. Confirma si participarás\\.", RegexOption.DOT_MATCHES_ALL), R.string.notification_followed_message)
)
