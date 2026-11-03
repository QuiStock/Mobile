package com.quistock.quistock.presentation.notifications

import com.quistock.quistock.domain.model.AlertTarget
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.RoleAlertType
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.presentation.navigation.MainTab

/**
 * Holds a tapped alert until the session can open it. It opens only for the same signed-in user
 * with a role entitled to it; while the session or profile is being established it waits, and any
 * other state discards it.
 */
internal class AlertLauncher(private val focusRequest: (String) -> Unit, private val openTab: (MainTab) -> Unit) {
    private var pending: AlertTarget? = null

    fun receive(target: AlertTarget?) {
        if (target != null) pending = target
    }

    fun open(state: SessionState, profile: ProfileState, blocked: Boolean) {
        val target = pending
        val waiting = state == SessionState.Restoring || state is SessionState.Failure ||
            (
                state == SessionState.Active && (profile !is ProfileState.Ready || blocked) &&
                    profile !is ProfileState.Rejected
                )
        if (target == null || waiting) return
        pending = null
        val ready = profile as? ProfileState.Ready
        if (state == SessionState.Active && ready != null && target.entitled(ready.profile)) {
            if (target.type == RoleAlertType.PENDING_APPROVAL) target.targetId?.let(focusRequest)
            openTab(
                if (target.type == RoleAlertType.NEAR_EXPIRY) MainTab.HOME else MainTab.INTERFERENCES,
            )
        }
    }
}
