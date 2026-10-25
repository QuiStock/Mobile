package com.quistock.quistock.presentation.common

import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.model.UserRole

object ManagerReviewPolicy {
    fun canShow(profile: ProfileState?, session: SessionState, generation: Long): Boolean =
        (profile as? ProfileState.Ready)?.let {
            it.generation == generation && session == SessionState.Active && it.profile.role == UserRole.MANAGER
        } == true
}
