package com.quistock.quistock.presentation.notifications

import android.content.Intent
import com.quistock.quistock.domain.model.AlertTarget
import com.quistock.quistock.domain.model.RoleAlertType

/** Only the alert type, target ID and intended user cross the notification boundary. */
object AlertTargetIntent {
    private const val TYPE = "com.quistock.quistock.alert.TYPE"
    private const val USER = "com.quistock.quistock.alert.USER"
    private const val TARGET = "com.quistock.quistock.alert.TARGET"

    fun put(intent: Intent, target: AlertTarget): Intent = intent.apply {
        putExtra(TYPE, target.type.name)
        putExtra(USER, target.userId)
        target.targetId?.let { putExtra(TARGET, it) }
    }

    fun read(intent: Intent?): AlertTarget? {
        val extras = intent?.extras
        val type = RoleAlertType.entries.find { it.name == extras?.getString(TYPE) }
        val user = extras?.getString(USER)?.takeIf { it.isNotBlank() }
        return if (type != null && user != null) {
            AlertTarget(type, user, extras.getString(TARGET)?.takeIf { it.isNotBlank() })
        } else {
            null
        }
    }

    /** Consumes the target so recreation or a later resume never reopens it. */
    fun take(intent: Intent?): AlertTarget? = read(intent).also {
        intent?.removeExtra(TYPE)
        intent?.removeExtra(USER)
        intent?.removeExtra(TARGET)
    }
}
