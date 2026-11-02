package com.quistock.quistock.presentation.control

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.quistock.quistock.R
import com.quistock.quistock.databinding.ItemProductControlBinding
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.StockFlow
import com.quistock.quistock.presentation.common.AlertProductPresentation

class ProductControlAdapter(private val onSelect: (AlertProduct) -> Unit) :
    ListAdapter<AlertProduct, ProductControlAdapter.Holder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemProductControlBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemProductControlBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(product: AlertProduct) {
            val context = binding.root.context
            binding.productName.text = product.name
            binding.productName.contentDescription = product.name
            binding.productMeta.text = AlertProductPresentation.quantity(context, product)
            binding.productFlow.setText(
                when (product.flow) {
                    StockFlow.HIGH -> R.string.product_control_high
                    StockFlow.MEDIUM -> R.string.product_control_medium
                    StockFlow.LOW -> R.string.product_control_low
                    null -> R.string.product_control_unavailable_flow
                },
            )
            binding.productExpiry.text = AlertProductPresentation.expiryDays(context, product.expirationDays)
            binding.productExpiryStatus.text = AlertProductPresentation.expiry(context, product.expirationDays)
            binding.root.setOnClickListener { onSelect(product) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<AlertProduct>() {
        override fun areItemsTheSame(oldItem: AlertProduct, newItem: AlertProduct) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AlertProduct, newItem: AlertProduct) = oldItem == newItem
    }
}
