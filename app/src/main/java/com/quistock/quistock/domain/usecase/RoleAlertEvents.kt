package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.ActionStatus
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.CreationRisk
import com.quistock.quistock.domain.model.InterferenceAction
import com.quistock.quistock.domain.model.RoleAlert
import com.quistock.quistock.domain.model.RoleAlertType
import java.time.LocalDate

/**
 * Pure rules turning Core reads into alert candidates. Each candidate has a delivery key:
 * near expiry once per product and business day, requests once per action ID.
 */
internal object RoleAlertEvents {
    const val MAX_INDIVIDUAL = 3
    private const val EXPIRY_KEY = "E:"
    private const val PENDING_KEY = "P:"
    private const val APPROVED_KEY = "A:"

    class Candidate(
        val type: RoleAlertType,
        val key: String,
        val targetId: String,
        val subject: String?,
        val action: InterferenceAction? = null,
    )

    fun expiring(catalog: List<AlertProduct>, today: LocalDate) = catalog.filter {
        CreationRisk.NEAR_EXPIRY in it.risks && CreationRisk.EXPIRED !in it.risks
    }.map { Candidate(RoleAlertType.NEAR_EXPIRY, "$EXPIRY_KEY$today:${it.id}", it.id, it.name) }

    fun pending(list: List<InterferenceAction>) = list.filter { it.status == ActionStatus.SENT_TO_MANAGER }
        .map { Candidate(RoleAlertType.PENDING_APPROVAL, "$PENDING_KEY${it.id}", it.id, it.productName, it) }

    fun approved(list: List<InterferenceAction>, own: Set<String>) =
        list.filter { it.id in own && it.status == ActionStatus.APPROVED }
            .map { Candidate(RoleAlertType.REQUEST_APPROVED, "$APPROVED_KEY${it.id}", it.id, it.productName, it) }

    /** Up to [MAX_INDIVIDUAL] alerts per type; more become one summary that opens the list. */
    fun group(userId: String, fresh: List<Candidate>): List<RoleAlert> =
        fresh.groupBy { it.type }.flatMap { (type, events) ->
            if (events.size > MAX_INDIVIDUAL) {
                listOf(RoleAlert(type, userId, null, count = events.size))
            } else {
                events.map { RoleAlert(type, userId, it.targetId, it.subject, it.action?.type) }
            }
        }

    /** Drops keys that can no longer repeat, so memory stays bounded. */
    fun prune(delivered: Set<String>, today: LocalDate, list: List<InterferenceAction>?): Set<String> {
        val stillPending = list?.let { pending(it).map(Candidate::key).toSet() }
        return delivered.filterTo(mutableSetOf()) { key ->
            when {
                key.startsWith(EXPIRY_KEY) -> key.startsWith("$EXPIRY_KEY$today:")
                key.startsWith(PENDING_KEY) -> stillPending?.contains(key) ?: true
                else -> false
            }
        }
    }
}
