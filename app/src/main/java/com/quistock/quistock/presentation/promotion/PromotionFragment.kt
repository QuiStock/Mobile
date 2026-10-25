package com.quistock.quistock.presentation.promotion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentPromotionBinding
import com.quistock.quistock.presentation.common.AlertProductNavigation
import com.quistock.quistock.presentation.common.PopupFeedback
import com.quistock.quistock.presentation.common.SkeletonLoadingScreen
import com.quistock.quistock.presentation.common.SkeletonScreenType
import com.quistock.quistock.presentation.common.bindAlertProductSummary

class PromotionFragment : Fragment() {
    private var binding: FragmentPromotionBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SkeletonLoadingScreen.wrap(
            FragmentPromotionBinding.inflate(inflater, container, false).also { binding = it }.root,
            SkeletonScreenType.PROMOTION,
        )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val currentBinding = binding ?: return
        val selectedProduct = AlertProductNavigation.product(arguments)
        selectedProduct?.let {
            view.bindAlertProductSummary(it)
            currentBinding.tvPrecoAtual.isVisible = false
            currentBinding.tvPrecoPromocional.isVisible = false
        }
        currentBinding.btnAlertBack.setOnClickListener { findNavController().popBackStack() }

        currentBinding.btnSubmitPromotion.setOnClickListener {
            if (selectedProduct != null) {
                PopupFeedback.showStatus(view, getString(R.string.home_alerts_submission_unavailable), true)
                return@setOnClickListener
            }
            if (currentBinding.etPromotionDiscount.text.toString().trim().isEmpty() ||
                currentBinding.etPromotionDuration.text.toString().trim().isEmpty()
            ) {
                PopupFeedback.showStatus(view, getString(R.string.popup_promotion_invalid), true)
                return@setOnClickListener
            }
            PopupFeedback.showConfirmation(requireContext(), getString(R.string.popup_promotion_confirm)) {
                findNavController().navigate(R.id.action_promotionFragment_to_promoSentFragment)
            }
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
