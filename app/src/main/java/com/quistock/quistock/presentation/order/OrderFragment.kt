package com.quistock.quistock.presentation.order

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.quistock.quistock.R
import com.quistock.quistock.presentation.common.SkeletonLoadingScreen
import com.quistock.quistock.presentation.common.SkeletonScreenType

class OrderFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SkeletonLoadingScreen.wrap(
            inflater.inflate(R.layout.fragment_order, container, false),
            SkeletonScreenType.ORDER,
        )

    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) = OrderFragment().apply {
            arguments = Bundle().apply {
                putString("param1", param1)
                putString("param2", param2)
            }
        }
    }
}
