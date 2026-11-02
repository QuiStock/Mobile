package com.quistock.quistock.presentation.interferences

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import com.quistock.quistock.domain.model.ActionDraft
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.AlertProduct
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

internal object CreationCardFormatter {
    private val productDateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.US)

    fun formatProductCard(context: Context, product: AlertProduct): CharSequence =
        CreationPresentation.product(context, product).let { value ->
            SpannableStringBuilder(value).apply {
                val end = value.indexOf('\n').takeIf { it >= 0 } ?: value.length
                setSpan(StyleSpan(Typeface.BOLD), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

    fun formatReviewSpannable(
        context: Context,
        type: ActionType,
        product: AlertProduct,
        draft: ActionDraft,
    ): CharSequence = SpannableStringBuilder(
        buildString {
            append(context.getString(CreationPresentation.type(type)))
            append("\n")
            append(product.name)
            append("\n\n")
            append(CreationPresentation.values(context, type, draft))
        },
    )

    fun formatSentDetails(summaryText: String, productText: String): CharSequence {
        val source = summaryText.ifBlank { productText }
        if (source.isBlank()) return source
        return source.lineSequence().joinToString("\n\n") { line ->
            val splitAt = line.indexOf(':')
            if (splitAt <= 0 || splitAt == line.lastIndex) {
                line
            } else {
                val label = line.substring(0, splitAt + 1)
                val rawValue = line.substring(splitAt + 1).trim()
                val value = if (label.contains("data", true) || label.contains("início", true) ||
                    label.contains("fim", true)
                ) {
                    formatAnalysisDate(rawValue)
                } else {
                    rawValue
                }
                "$label $value"
            }
        }
    }

    fun formatAnalysisDate(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return try {
            Instant.parse(value).atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate().format(productDateFormatter)
        } catch (_: DateTimeParseException) {
            ActionDraft.date(value)?.format(productDateFormatter) ?: value.trim()
        }
    }
}
