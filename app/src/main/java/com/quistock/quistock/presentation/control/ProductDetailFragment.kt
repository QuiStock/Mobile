package com.quistock.quistock.presentation.control

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentProductDetailBinding
import com.quistock.quistock.presentation.common.SkeletonLoadingScreen
import com.quistock.quistock.presentation.common.SkeletonScreenType

class ProductDetailFragment : Fragment() {
    private var binding: FragmentProductDetailBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SkeletonLoadingScreen.wrap(
            FragmentProductDetailBinding.inflate(inflater, container, false).also { binding = it }.root,
            SkeletonScreenType.PRODUCT_DETAIL,
        )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.btnVoltarControle?.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
