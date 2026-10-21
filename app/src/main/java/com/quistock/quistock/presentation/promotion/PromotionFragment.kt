package com.quistock.quistock.presentation.promotion

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentPromotionBinding
import com.quistock.quistock.presentation.common.PopupFeedback

class PromotionFragment : Fragment(R.layout.fragment_promotion) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentPromotionBinding.bind(view)

        binding.btnSubmitPromotion.setOnClickListener {
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
