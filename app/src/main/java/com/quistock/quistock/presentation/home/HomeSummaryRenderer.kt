package com.quistock.quistock.presentation.home

import android.content.Context
import androidx.core.view.isVisible
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentHomeBinding
import com.quistock.quistock.domain.model.SummaryFailure
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal class HomeSummaryRenderer(private val context: Context, private val views: FragmentHomeBinding) {
    fun render(state: HomeSummaryUiState, recovering: Boolean) {
        val summary = state.cached?.summary
        val unavailable = context.getString(R.string.summary_unavailable)
        val formatter = NumberFormat.getIntegerInstance(Locale.getDefault())
        val values = listOf(summary?.nearExpiry, summary?.stockoutAndOverstock, summary?.activeActions)
        val labels = listOf(R.string.summary_expiry_label, R.string.summary_stock_label, R.string.summary_actions_label)
        listOf(views.tvNumeroVencimento, views.tvNumeroRuptura, views.tvNumeroAcoes).forEachIndexed { index, view ->
            view.isSaveEnabled = false
            val value = values[index]?.let(formatter::format) ?: unavailable
            view.text = value
            view.contentDescription =
                context.getString(R.string.summary_metric, context.getString(labels[index]), value)
        }
        views.summaryProgress.isVisible = state.loading || recovering
        views.summaryRetry.isVisible = state.failure != null || state.offline
        views.summaryRetry.isEnabled = !state.loading && !recovering && state.retryAt <= state.now
        views.summaryRetry.setText(if (state.offline) R.string.summary_reconnect else R.string.summary_retry)
        views.summaryLogout.isVisible = state.offline
        val message = if (state.offline) R.string.summary_offline else state.failure?.let(::failureMessage)
        views.summaryMessage.isVisible = message != null
        message?.let(views.summaryMessage::setText)
        views.summaryStale.isVisible = state.cached?.staleAt(state.now) == true
        state.cached?.let {
            val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Locale.getDefault())
                .format(Instant.ofEpochMilli(it.receivedAt).atZone(ZoneId.of("America/Sao_Paulo")))
            views.summaryStale.text = context.getString(R.string.summary_stale, date)
        }
        views.summaryPersistence.isVisible = state.persistenceFailed
        views.alertsSection.isVisible = !state.offline
        views.tvTituloAlertas.isVisible = !state.offline
        views.bottomNavigation.isVisible = !state.offline
        views.btnPerfil.isVisible = !state.offline
        views.btnNotificacoes.isVisible = !state.offline
    }

    private fun failureMessage(reason: SummaryFailure): Int = when (reason) {
        SummaryFailure.NETWORK -> R.string.session_network
        SummaryFailure.TIMEOUT -> R.string.session_timeout
        SummaryFailure.SERVER -> R.string.session_server
        SummaryFailure.FORBIDDEN -> R.string.summary_forbidden
        SummaryFailure.NO_STORE -> R.string.summary_no_store
        SummaryFailure.RATE_LIMIT -> R.string.summary_rate_limit
        SummaryFailure.CONTRACT -> R.string.summary_error
    }
}
