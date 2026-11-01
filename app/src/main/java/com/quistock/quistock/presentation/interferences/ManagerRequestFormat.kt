package com.quistock.quistock.presentation.interferences

import android.content.Context
import android.widget.TextView
import androidx.core.view.isVisible
import com.quistock.quistock.R
import com.quistock.quistock.domain.model.ActionDraft
import com.quistock.quistock.domain.model.ActionStatus
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.InterferenceAction
import com.quistock.quistock.domain.model.StockFlow
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Pure formatting of persisted values; missing values stay null so the UI shows "Não informado". */
internal object RequestFormat {
    private val BRAZIL = Locale.forLanguageTag("pt-BR")
    private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", BRAZIL)
    private val SHORT_DATE = DateTimeFormatter.ofPattern("dd/MM", BRAZIL)

    fun date(value: String): String? = ActionDraft.date(value)?.format(DATE)

    fun shortDate(value: String): String? = ActionDraft.date(value)?.format(SHORT_DATE)

    /** Inclusive day count, matching Core's promotion duration rule. */
    fun durationDays(start: String, end: String): Int? {
        val first = ActionDraft.date(start)
        val last = ActionDraft.date(end)
        return if (first == null || last == null || last.isBefore(first)) {
            null
        } else {
            ChronoUnit.DAYS.between(first, last).toInt() + 1
        }
    }

    fun number(value: BigDecimal): String = value.stripTrailingZeros().toPlainString().replace('.', ',')

    fun number(value: String): String? = value.toBigDecimalOrNull()?.let(::number)
}

/** Resource-backed copy shared by the hub, detail and approval screens. */
internal class RequestText(private val context: Context) {
    private val notInformed get() = context.getString(R.string.manager_not_informed)

    fun title(action: InterferenceAction, product: AlertProduct?): String =
        product?.name ?: action.productName ?: context.getString(R.string.manager_unknown_product)

    fun type(type: ActionType): String = context.getString(
        if (type == ActionType.ORDER) R.string.manager_type_order else R.string.manager_type_promotion,
    )

    fun quantity(action: InterferenceAction): String =
        RequestFormat.number(action.values.quantity)?.let { context.getString(R.string.manager_quantity, it) }
            ?: notInformed

    fun desiredDate(action: InterferenceAction): String = RequestFormat.date(action.values.orderDate) ?: notInformed

    fun discount(action: InterferenceAction): String =
        RequestFormat.number(action.values.discount)?.let { context.getString(R.string.manager_discount, it) }
            ?: notInformed

    fun period(action: InterferenceAction): String {
        val start = RequestFormat.shortDate(action.values.start)
        val end = RequestFormat.shortDate(action.values.end)
        return if (start != null && end != null) context.getString(R.string.manager_period, start, end) else notInformed
    }

    fun duration(action: InterferenceAction): String {
        val days = RequestFormat.durationDays(action.values.start, action.values.end) ?: return notInformed
        return context.resources.getQuantityString(R.plurals.manager_duration, days, days, period(action))
    }

    /** One-line summary for list cards. */
    fun summary(action: InterferenceAction): String = type(action.type) + " · " + when (action.type) {
        ActionType.ORDER -> quantity(action) + " · " + desiredDate(action)
        ActionType.PROMOTION -> discount(action) + " · " + period(action)
    }

    fun reason(action: InterferenceAction): String = action.justification ?: notInformed

    fun status(status: ActionStatus): String = context.getString(
        when (status) {
            ActionStatus.APPROVED -> R.string.manager_status_approved
            ActionStatus.REJECTED -> R.string.manager_status_rejected
            else -> R.string.manager_status_pending
        },
    )
}

/** Product context from the store catalog; absent data stays "Não informado". */
internal class ProductText(private val context: Context) {
    private val notInformed get() = context.getString(R.string.manager_not_informed)

    fun flow(flow: StockFlow?): String? = when (flow) {
        StockFlow.HIGH -> context.getString(R.string.manager_flow_high)
        StockFlow.MEDIUM -> context.getString(R.string.manager_flow_medium)
        StockFlow.LOW -> context.getString(R.string.manager_flow_low)
        null -> null
    }

    fun categoryStock(product: AlertProduct?): String = product?.let {
        context.getString(R.string.manager_category_stock, it.category, RequestFormat.number(it.stock))
    } ?: notInformed

    fun expiry(product: AlertProduct?): String {
        val days = product?.expirationDays ?: return notInformed
        return when {
            days > 0 -> context.resources.getQuantityString(R.plurals.manager_expiry_days, days, days)
            days == 0 -> context.getString(R.string.manager_expiry_today)
            else -> context.resources.getQuantityString(R.plurals.manager_expired, -days, -days)
        }
    }
}

/** Status pill: color is paired with text, never the only signal. */
internal fun TextView.showStatus(status: ActionStatus) {
    val (background, color) = when (status) {
        ActionStatus.APPROVED -> R.drawable.bg_badge_approved to R.color.color_limao
        ActionStatus.REJECTED -> R.drawable.bg_badge_rejected to R.color.color_rosa_tag
        else -> R.drawable.bg_badge_pending to R.color.color_amarelo_tag
    }
    setBackgroundResource(background)
    setTextColor(context.getColor(color))
    text = RequestText(context).status(status)
    isVisible = true
}
