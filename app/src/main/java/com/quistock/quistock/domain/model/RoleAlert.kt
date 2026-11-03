package com.quistock.quistock.domain.model

/** Device alert kinds; each one belongs to the roles that may act on it. */
enum class RoleAlertType(val roles: Set<UserRole>) {
    NEAR_EXPIRY(setOf(UserRole.EMPLOYEE, UserRole.MANAGER)),
    PENDING_APPROVAL(setOf(UserRole.MANAGER)),
    REQUEST_APPROVED(setOf(UserRole.EMPLOYEE)),
    ;

    companion object {
        fun forRole(role: UserRole): List<RoleAlertType> = entries.filter { role in it.roles }
    }
}

/**
 * One deliverable alert. [targetId] is a product ID (near expiry) or an action ID; a null target
 * means a summary of [count] events that opens the list context.
 */
class RoleAlert(
    val type: RoleAlertType,
    val userId: String,
    val targetId: String?,
    val subject: String? = null,
    val actionType: ActionType? = null,
    val count: Int = 1,
) {
    override fun toString(): String = "RoleAlert($type)"
}

/** What a tapped alert asks to open; carries no product names or profile data. */
data class AlertTarget(val type: RoleAlertType, val userId: String, val targetId: String?) {
    fun entitled(profile: UserProfile): Boolean =
        profile.id == userId && profile.role in type.roles && profile.rejection() == null
}

/** Per-user memory kept on the device. Keys never contain product names. */
data class AlertMemory(
    val disabled: Set<RoleAlertType> = emptySet(),
    val delivered: Set<String> = emptySet(),
    val ownRequests: Set<String> = emptySet(),
)
