package com.quistock.quistock.presentation.control

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
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

        for (cardId in productCardIds) {
            view.findViewById<View>(cardId).setOnClickListener {
                findNavController()
                    .navigate(R.id.action_productControlFragment_to_productDetailFragment)
            }
        }

        view.findViewById<View>(R.id.dropdownFiltroFluxo)?.setOnClickListener { anchor ->
            val popup = PopupMenu(requireContext(), anchor)
            popup.menu.add("Todos os fluxos")
            popup.menu.add("Fluxo Alto")
            popup.menu.add("Fluxo Médio")
            popup.menu.add("Fluxo Baixo")
            popup.setOnMenuItemClickListener { item ->
                view.findViewById<TextView>(R.id.tvFiltroFluxo)?.text = item.title
                true
            }
            popup.show()
        }
    }
}
