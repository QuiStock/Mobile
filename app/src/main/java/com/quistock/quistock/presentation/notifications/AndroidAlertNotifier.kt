package com.quistock.quistock.presentation.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.quistock.quistock.R
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.AlertTarget
import com.quistock.quistock.domain.model.RoleAlert
import com.quistock.quistock.domain.model.RoleAlertType
import com.quistock.quistock.domain.port.AlertNotifier

/** Posts private notifications: product names never appear on the lock screen. */
class AndroidAlertNotifier(context: Context) : AlertNotifier {
    private val context = context.applicationContext
    private val manager = NotificationManagerCompat.from(this.context)

    override fun canNotify(): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && manager.areNotificationsEnabled()
    }

    override fun post(alerts: List<RoleAlert>) {
        if (alerts.isEmpty() || !canNotify()) return
        ensureChannels()
        alerts.forEach { alert ->
            try {
                manager.notify(notificationId(alert), build(alert))
            } catch (_: SecurityException) {
                return // Permission revoked between the check and the post.
            }
        }
    }

    override fun clearAll() = manager.cancelAll()

    private fun build(alert: RoleAlert) = NotificationCompat.Builder(context, channel(alert.type))
        .setSmallIcon(R.drawable.ic_notification)
        .setColor(ContextCompat.getColor(context, R.color.color_violeta))
        .setContentTitle(title(alert))
        .setContentText(text(alert))
        .setStyle(NotificationCompat.BigTextStyle().bigText(text(alert)))
        .setAutoCancel(true)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setPublicVersion(
            NotificationCompat.Builder(context, channel(alert.type))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.role_alert_public_title))
                .setContentText(context.getString(R.string.role_alert_public_text))
                .build(),
        )
        .setContentIntent(open(alert))
        .build()

    private fun open(alert: RoleAlert): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        AlertTargetIntent.put(launch, AlertTarget(alert.type, alert.userId, alert.targetId))
        return PendingIntent.getActivity(
            context,
            notificationId(alert),
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun title(alert: RoleAlert): String = when {
        alert.targetId == null -> summary(alert)
        alert.type == RoleAlertType.NEAR_EXPIRY -> context.getString(R.string.role_alert_expiry_title)
        alert.type == RoleAlertType.PENDING_APPROVAL -> context.getString(R.string.role_alert_pending_title)
        else -> context.getString(R.string.role_alert_approved_title)
    }

    private fun text(alert: RoleAlert): String {
        if (alert.targetId == null) return context.getString(R.string.role_alert_summary_text)
        val subject =
            alert.subject?.takeIf { it.isNotBlank() } ?: context.getString(R.string.role_alert_unknown_product)
        val order = alert.actionType == ActionType.ORDER
        val text = when (alert.type) {
            RoleAlertType.NEAR_EXPIRY -> R.string.role_alert_expiry_text

            RoleAlertType.PENDING_APPROVAL -> {
                if (order) R.string.role_alert_pending_order else R.string.role_alert_pending_promotion
            }

            RoleAlertType.REQUEST_APPROVED -> {
                if (order) R.string.role_alert_approved_order else R.string.role_alert_approved_promotion
            }
        }
        return context.getString(text, subject)
    }

    private fun summary(alert: RoleAlert): String = context.resources.getQuantityString(
        when (alert.type) {
            RoleAlertType.NEAR_EXPIRY -> R.plurals.role_alert_expiry_summary
            RoleAlertType.PENDING_APPROVAL -> R.plurals.role_alert_pending_summary
            RoleAlertType.REQUEST_APPROVED -> R.plurals.role_alert_approved_summary
        },
        alert.count,
        alert.count,
    )

    private fun ensureChannels() {
        RoleAlertType.entries.forEach { type ->
            val name = context.getString(
                when (type) {
                    RoleAlertType.NEAR_EXPIRY -> R.string.role_alert_channel_expiry
                    RoleAlertType.PENDING_APPROVAL -> R.string.role_alert_channel_pending
                    RoleAlertType.REQUEST_APPROVED -> R.string.role_alert_channel_approved
                },
            )
            val channel = NotificationChannel(channel(type), name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun channel(type: RoleAlertType) = "role_alert_${type.name.lowercase()}"

    private fun notificationId(alert: RoleAlert): Int =
        "${alert.userId}|${alert.type}|${alert.targetId ?: "*"}".hashCode()
}
