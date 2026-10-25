package com.quistock.quistock.presentation.order

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentOrderBinding
import com.quistock.quistock.presentation.common.AlertProductNavigation
import com.quistock.quistock.presentation.common.PopupFeedback
import com.quistock.quistock.presentation.common.SkeletonLoadingScreen
import com.quistock.quistock.presentation.common.SkeletonScreenType
import com.quistock.quistock.presentation.common.bindAlertProductSummary

class OrderFragment : Fragment() {
    private var binding: FragmentOrderBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SkeletonLoadingScreen.wrap(
            FragmentOrderBinding.inflate(inflater, container, false).also { binding = it }.root,
            SkeletonScreenType.ORDER,
        )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val currentBinding = binding ?: return
        val selectedProduct = AlertProductNavigation.product(arguments)
        selectedProduct?.let { view.bindAlertProductSummary(it) }
        currentBinding.btnAlertBack.setOnClickListener { findNavController().popBackStack() }

        currentBinding.btnSubmitOrder.setOnClickListener {
            if (selectedProduct != null) {
                PopupFeedback.showStatus(view, getString(R.string.home_alerts_submission_unavailable), true)
                return@setOnClickListener
            }
            if (currentBinding.etOrderQuantity.text.toString().trim().isEmpty() ||
                currentBinding.etOrderDate.text.toString().trim().isEmpty()
            ) {
                PopupFeedback.showStatus(view, getString(R.string.popup_order_invalid), true)
                return@setOnClickListener
            }
            PopupFeedback.showConfirmation(requireContext(), getString(R.string.popup_order_confirm)) {
                findNavController().navigate(R.id.action_orderFragment_to_orderSentFragment)
            }
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
