package com.quistock.quistock.presentation.control

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.quistock.quistock.R
import com.quistock.quistock.presentation.common.SkeletonLoadingScreen
import com.quistock.quistock.presentation.common.SkeletonScreenType

class ProductControlFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SkeletonLoadingScreen.wrap(
            inflater.inflate(R.layout.fragment_product_control, container, false),
            SkeletonScreenType.PRODUCT_CONTROL,
        )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val productCardIds = intArrayOf(
            R.id.cardProdutoFluxoAlto,
            R.id.cardProdutoFluxoMedio,
            R.id.cardProdutoFluxoBaixo,
            R.id.cardProdutoFluxoAlto2,
            R.id.cardProdutoFluxoMedio2,
            R.id.cardProdutoFluxoBaixo2,
        )

        productCardIds.forEach { cardId ->
            view.findViewById<View>(cardId).setOnClickListener { clickedView ->
                Navigation.findNavController(clickedView)
                    .navigate(R.id.action_productControlFragment_to_productDetailFragment)
            }
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) = ProductControlFragment().apply {
            arguments = Bundle().apply {
                putString("param1", param1)
                putString("param2", param2)
            }
        }
    }
}
