package com.quistock.quistock.presentation.control

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentProductDetailBinding
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.StockFlow
import com.quistock.quistock.presentation.activity.SessionViewModel
import com.quistock.quistock.presentation.chatbot.ChatbotViewModel
import com.quistock.quistock.presentation.common.AlertProductNavigation
import com.quistock.quistock.presentation.common.AlertProductPresentation
import com.quistock.quistock.presentation.common.SkeletonLoadingScreen
import com.quistock.quistock.presentation.common.SkeletonScreenType
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class ProductDetailFragment : Fragment() {
    private var binding: FragmentProductDetailBinding? = null
    private val productViewModel: ProductControlViewModel by activityViewModel()
    private val sessionViewModel: SessionViewModel by activityViewModel()
    private val chatbotViewModel: ChatbotViewModel by activityViewModel()
    private var navigating = false
    private var selectedProduct: AlertProduct? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SkeletonLoadingScreen.wrap(
            FragmentProductDetailBinding.inflate(inflater, container, false).also { binding = it }.root,
            SkeletonScreenType.PRODUCT_DETAIL,
        )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = requireNotNull(binding)
        selectedProduct = AlertProductNavigation.product(arguments)
        renderSelection()
        views.btnVoltarControle.setOnClickListener {
            findNavController().navigateUp()
        }
        views.btnRecomendacaoFefo.setOnClickListener { openFefo() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    productViewModel.state,
                    sessionViewModel.session.state,
                    sessionViewModel.profile.state,
                ) { _, _, _ -> Unit }.collect { renderSelection() }
            }
        }
    }

    override fun onDestroyView() {
        binding = null
        selectedProduct = null
        super.onDestroyView()
    }

    override fun onStart() {
        super.onStart()
        navigating = false
    }

    override fun onResume() {
        super.onResume()
        renderSelection()
    }

    private fun renderSelection() {
        val views = binding ?: return
        val product = selectedProduct?.takeIf(productViewModel::canUse)
        bindProduct(views, product)
        views.btnRecomendacaoFefo.isEnabled = product != null
    }

    private fun bindProduct(views: FragmentProductDetailBinding, product: AlertProduct?) {
        bindProductIdentity(views, product)
        bindProductSummary(views, product)
        bindProductMetrics(views, product)
    }

    private fun bindProductIdentity(views: FragmentProductDetailBinding, product: AlertProduct?) {
        val unavailable = getString(R.string.product_control_unavailable)
        views.detailProductTitle.text = product?.name ?: unavailable
        views.detailProductSubtitle.text = product?.let {
            AlertProductPresentation.skuCategory(requireContext(), it.sku, it.category)
        } ?: unavailable
    }

    private fun bindProductSummary(views: FragmentProductDetailBinding, product: AlertProduct?) {
        val unavailable = getString(R.string.product_control_unavailable)
        views.detailSummaryName.text = product?.name ?: unavailable
        views.detailSummaryMeta.text = product?.let {
            AlertProductPresentation.quantity(requireContext(), it)
        } ?: unavailable
        views.detailFlow.setText(
            when (product?.flow) {
                StockFlow.HIGH -> R.string.product_control_high
                StockFlow.MEDIUM -> R.string.product_control_medium
                StockFlow.LOW -> R.string.product_control_low
                null -> R.string.product_control_unavailable_flow
            },
        )
        views.detailExpiryDays.text = AlertProductPresentation.expiryDays(requireContext(), product?.expirationDays)
        views.detailExpiryStatus.text = product?.let {
            AlertProductPresentation.expiry(requireContext(), it.expirationDays)
        } ?: unavailable
    }

    private fun bindProductMetrics(views: FragmentProductDetailBinding, product: AlertProduct?) {
        val unavailable = getString(R.string.product_control_unavailable)
        views.detailSalesValue.text = product?.sales7d?.let {
            AlertProductPresentation.formatAverageDailySales(it)
        } ?: unavailable
        views.detailStockValue.text = product?.let { AlertProductPresentation.formatStock(it.stock) } ?: unavailable
        views.detailValidityValue.text = product?.let {
            AlertProductPresentation.expiry(requireContext(), it.expirationDays)
        } ?: unavailable
    }

    private fun openFefo() {
        val product = selectedProduct?.takeIf(productViewModel::canUse)
        val controller = findNavController()
        if (navigating || controller.currentDestination?.id != R.id.productDetailFragment) return
        if (product == null) return
        chatbotViewModel.offerFefo(product)
        navigating = true
        controller.navigate(R.id.action_productDetailFragment_to_chatbotFragment)
    }
}
