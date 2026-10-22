package com.quistock.quistock.presentation.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.quistock.quistock.R
import com.quistock.quistock.databinding.ItemAlertaBinding
import com.quistock.quistock.domain.model.AlertRisk
import com.quistock.quistock.domain.model.PriorityAlert
import com.quistock.quistock.presentation.common.AlertProductPresentation

class PriorityAlertsAdapter(
    private val onAction: (PriorityAlert) -> Unit,
    private val onHelp: () -> Unit,
) : ListAdapter<PriorityAlert, PriorityAlertsAdapter.Holder>(AlertDiff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemAlertaBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemAlertaBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(alert: PriorityAlert) {
            val product = alert.product
            binding.tvProduto.text = product.name
            binding.tvProduto.contentDescription = product.name
            binding.tvCategoria.text = AlertProductPresentation.quantity(binding.root.context, product)
            binding.tvData.text = AlertProductPresentation.expiry(binding.root.context, product.expirationDays)
            binding.tvRisco.setText(
                when (alert.risk) {
                    AlertRisk.EXPIRY -> R.string.home_alerts_expiry_risk
                    AlertRisk.STOCKOUT -> R.string.home_alerts_stockout_risk
                    AlertRisk.OVERSTOCK -> R.string.home_alerts_overstock_risk
                },
            )
            binding.btnPromocao.setText(
                if (alert.risk == AlertRisk.STOCKOUT) R.string.home_alerts_order else R.string.home_alerts_promotion,
            )
            binding.btnPromocao.setOnClickListener { onAction(alert) }
            binding.btnFefo.setOnClickListener { onHelp() }
        }
    }

    private object AlertDiff : DiffUtil.ItemCallback<PriorityAlert>() {
        override fun areItemsTheSame(oldItem: PriorityAlert, newItem: PriorityAlert) =
            oldItem.product.id == newItem.product.id

        override fun areContentsTheSame(oldItem: PriorityAlert, newItem: PriorityAlert) = oldItem == newItem
    }
}
