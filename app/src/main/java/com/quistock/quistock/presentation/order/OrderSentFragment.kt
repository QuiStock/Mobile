package com.quistock.quistock.presentation.order

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.quistock.quistock.R
import com.quistock.quistock.presentation.common.PopupFeedback

class OrderSentFragment : Fragment(R.layout.fragment_order_sent) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        PopupFeedback.showStatus(view, getString(R.string.popup_order_sent), false)
    }
}
