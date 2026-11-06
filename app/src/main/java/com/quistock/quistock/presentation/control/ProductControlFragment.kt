package com.quistock.quistock.presentation.control

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentProductControlBinding
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.presentation.common.AlertProductNavigation
import com.quistock.quistock.presentation.common.SkeletonLoadingScreen
import com.quistock.quistock.presentation.common.SkeletonScreenType
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class ProductControlFragment : Fragment() {
    private var binding: FragmentProductControlBinding? = null
    private val viewModel: ProductControlViewModel by activityViewModel()
    private var navigating = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SkeletonLoadingScreen.wrap(
            FragmentProductControlBinding.inflate(inflater, container, false).also { binding = it }.root,
            SkeletonScreenType.PRODUCT_CONTROL,
        )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val views = requireNotNull(binding)
        val adapter = ProductControlAdapter(::openProduct)
        views.rvProducts.layoutManager = LinearLayoutManager(requireContext())
        views.rvProducts.adapter = adapter
        views.productControlRetry.setOnClickListener { viewModel.retry() }
        views.etBuscaProduto.setText(viewModel.search.value)
        views.etBuscaProduto.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                viewModel.setSearch(s?.toString().orEmpty())
                render(viewModel.state.value, adapter)
            }
        })
        views.dropdownFiltroFluxo.setOnClickListener { anchor ->
            val popup = PopupMenu(requireContext(), anchor)
            val filters = listOf(
                R.string.product_control_all_flows to ProductFlowFilter.ALL,
                R.string.product_control_high to ProductFlowFilter.HIGH,
                R.string.product_control_medium to ProductFlowFilter.MEDIUM,
                R.string.product_control_low to ProductFlowFilter.LOW,
                R.string.product_control_unavailable_flow to ProductFlowFilter.UNAVAILABLE,
            )
            filters.forEach { (label, _) -> popup.menu.add(label) }
            popup.setOnMenuItemClickListener { item ->
                val selected = filters.firstOrNull { getString(it.first) == item.title }?.second
                    ?: return@setOnMenuItemClickListener false
                viewModel.setFilter(selected)
                renderFilter(views)
                render(viewModel.state.value, adapter)
                true
            }
            popup.show()
        }
        views.btnLimparFiltroFluxo.setOnClickListener {
            viewModel.setFilter(ProductFlowFilter.ALL)
            renderFilter(views)
            render(viewModel.state.value, adapter)
        }
        renderFilter(views)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it, adapter) }
            }
        }
        viewModel.enter()
        render(viewModel.state.value, adapter)
    }

    override fun onStart() {
        super.onStart()
        navigating = false
    }

    override fun onDestroyView() {
        binding?.rvProducts?.adapter = null
        binding = null
        super.onDestroyView()
    }

    private fun renderFilter(views: FragmentProductControlBinding) {
        val label = when (viewModel.filter.value) {
            ProductFlowFilter.ALL -> R.string.product_control_filter
            ProductFlowFilter.HIGH -> R.string.product_control_high
            ProductFlowFilter.MEDIUM -> R.string.product_control_medium
            ProductFlowFilter.LOW -> R.string.product_control_low
            ProductFlowFilter.UNAVAILABLE -> R.string.product_control_unavailable_flow
        }
        val active = viewModel.filter.value != ProductFlowFilter.ALL
        views.tvFiltroFluxo.setText(label)
        views.ivSetaFiltroFluxo.isVisible = !active
        views.btnLimparFiltroFluxo.isVisible = active
    }

    private fun render(state: ProductControlState, adapter: ProductControlAdapter) {
        val views = binding ?: return
        views.productControlProgress.isVisible = state == ProductControlState.Loading
        views.productControlRetry.isVisible = state == ProductControlState.Error
        val ready = state as? ProductControlState.Ready
        val products = viewModel.visibleProducts()
        views.rvProducts.isVisible = ready != null && products.isNotEmpty()
        adapter.submitList(products)
        val message = when {
            state == ProductControlState.Error -> R.string.product_control_error
            ready != null && ready.products.isEmpty() -> R.string.product_control_empty
            ready != null && products.isEmpty() -> R.string.product_control_no_matches
            ready?.partial == true -> R.string.product_control_partial
            else -> null
        }
        views.productControlMessage.isVisible = message != null
        message?.let { views.productControlMessage.setText(it) }
    }

    private fun openProduct(product: AlertProduct) {
        val controller = findNavController()
        if (navigating || !viewModel.canUse(product) ||
            controller.currentDestination?.id != R.id.productControlFragment
        ) {
            return
        }
        navigating = true
        controller.navigate(
            R.id.action_productControlFragment_to_productDetailFragment,
            AlertProductNavigation.arguments(product),
        )
    }
}
