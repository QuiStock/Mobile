package com.quistock.quistock.presentation.order

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentOrderBinding
import com.quistock.quistock.presentation.common.PopupFeedback

class OrderFragment : Fragment(R.layout.fragment_order) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentOrderBinding.bind(view)

        binding.btnSubmitOrder.setOnClickListener {
            if (binding.etOrderQuantity.text.toString().trim().isEmpty() ||
                binding.etOrderDate.text.toString().trim().isEmpty()
            ) {
                PopupFeedback.showStatus(view, getString(R.string.popup_order_invalid), true)
                return@setOnClickListener
            }
            PopupFeedback.showConfirmation(requireContext(), getString(R.string.popup_order_confirm)) {
                findNavController().navigate(R.id.action_orderFragment_to_orderSentFragment)
            }
        }
    }
}
