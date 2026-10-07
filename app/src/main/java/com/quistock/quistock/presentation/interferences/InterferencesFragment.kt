package com.quistock.quistock.presentation.interferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.quistock.quistock.R
import com.quistock.quistock.presentation.common.SkeletonLoadingScreen
import com.quistock.quistock.presentation.common.SkeletonScreenType

class InterferencesFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SkeletonLoadingScreen.wrap(
            inflater.inflate(R.layout.fragment_interferences, container, false),
            SkeletonScreenType.INTERFERENCES,
        )

    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) = InterferencesFragment().apply {
            arguments = Bundle().apply {
                putString("param1", param1)
                putString("param2", param2)
            }
        }
    }
}
