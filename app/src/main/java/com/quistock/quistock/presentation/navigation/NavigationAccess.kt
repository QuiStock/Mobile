package com.quistock.quistock.presentation.navigation

import com.quistock.quistock.R
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.presentation.activity.SessionViewModel

internal class NavigationAccess(private val model: SessionViewModel) {
    private val managerDestinations = setOf(
        R.id.employeeFragment,
        R.id.employeeDetailFragment,
        R.id.employeeProfileFragment,
        R.id.editEmployeeFragment,
        R.id.managerInterferencesFragment,
        R.id.managerPromotionDetailFragment,
        R.id.managerOrderDetailFragment,
        R.id.managerPromoApprovedFragment,
        R.id.managerOrderApprovedFragment,
    )

    fun ready(): ProfileState.Ready? {
        val ready = model.profile.state.value as? ProfileState.Ready
        val current = ready?.generation == model.session.snapshot().generation
        val active = model.session.state.value == SessionState.Active
        return ready?.takeIf { current && active && it.profile.role.hasAccess }
    }

    fun rejects(destination: Int?): Boolean =
        ready()?.profile?.role == UserRole.EMPLOYEE && destination in managerDestinations

    fun shouldClear(): Boolean = when (model.session.state.value) {
        SessionState.Active, SessionState.Restoring, is SessionState.Failure -> false
        else -> true
    }
}
