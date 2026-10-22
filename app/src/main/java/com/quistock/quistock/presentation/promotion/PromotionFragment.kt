package com.quistock.quistock.presentation.promotion

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.core.view.isVisible
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentPromotionBinding
import com.quistock.quistock.presentation.common.PopupFeedback
import com.quistock.quistock.presentation.common.AlertProductNavigation
import com.quistock.quistock.presentation.common.bindAlertProductSummary

class PromotionFragment : Fragment(R.layout.fragment_promotion) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentPromotionBinding.bind(view)
        val selectedProduct = AlertProductNavigation.product(arguments)
        selectedProduct?.let {
            view.bindAlertProductSummary(it)
            binding.tvPrecoAtual.isVisible = false
            binding.tvPrecoPromocional.isVisible = false
        }
        binding.btnAlertBack.setOnClickListener { findNavController().popBackStack() }

        binding.btnSubmitPromotion.setOnClickListener {
            if (selectedProduct != null) {
                PopupFeedback.showStatus(view, getString(R.string.home_alerts_submission_unavailable), true)
                return@setOnClickListener
            }
            if (binding.etPromotionDiscount.text.toString().trim().isEmpty() ||
                binding.etPromotionDuration.text.toString().trim().isEmpty()
            ) {
                PopupFeedback.showStatus(view, getString(R.string.popup_promotion_invalid), true)
                return@setOnClickListener
            }
            PopupFeedback.showConfirmation(requireContext(), getString(R.string.popup_promotion_confirm)) {
                findNavController().navigate(R.id.action_promotionFragment_to_promoSentFragment)
            }
        }
    }
}
