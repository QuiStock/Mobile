package com.quistock.quistock.presentation.promotion

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.quistock.quistock.R
import com.quistock.quistock.presentation.common.PopupFeedback

class PromoSentFragment : Fragment(R.layout.fragment_promo_sent) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        PopupFeedback.showStatus(view, getString(R.string.popup_promotion_sent), false)
    }
}
