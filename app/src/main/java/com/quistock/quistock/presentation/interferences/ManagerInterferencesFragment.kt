package com.quistock.quistock.presentation.interferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentManagerInterferencesBinding
import com.quistock.quistock.databinding.ItemManagerRequestBinding
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.InterferenceAction
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/** Manager hub: requests awaiting a decision and the store's recorded decisions. */
class ManagerInterferencesFragment : Fragment() {
    private var binding: FragmentManagerInterferencesBinding? = null
    private val model: ManagerInterferencesViewModel by activityViewModel()
    private var rendered: ManagerState? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentManagerInterferencesBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = requireNotNull(binding)
        views.tvTituloDecididas.setText(R.string.manager_decided_title)
        views.cardPromocao.setOnClickListener { model.toggleFilter(ActionType.PROMOTION) }
        views.cardPedidoEstoque.setOnClickListener { model.toggleFilter(ActionType.ORDER) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { render(it) }
            }
        }
        model.refresh()
    }

    override fun onDestroyView() {
        binding = null
        rendered = null
        super.onDestroyView()
    }

    private fun render(state: ManagerState) {
        val views = binding ?: return
        if (state.focusId != null && !state.loading) model.takeFocus()?.let(::open)
        renderFilters(views, state.filter)
        val previous = rendered
        val listChanged = previous == null || previous.actions != state.actions || previous.filter != state.filter ||
            previous.loading != state.loading || previous.listFailed != state.listFailed ||
            previous.products != state.products
        rendered = state
        if (!listChanged) return
        val text = RequestText(requireContext())
        fill(views.containerPendentes, state.pending, text, pendingMessage(state))
        fill(views.containerDecididas, state.decided, text, decidedMessage(state))
    }

    private fun renderFilters(views: FragmentManagerInterferencesBinding, filter: ActionType?) {
        val text = RequestText(requireContext())
        listOf(views.cardPromocao to ActionType.PROMOTION, views.cardPedidoEstoque to ActionType.ORDER)
            .forEach { (card, type) ->
                card.isSelected = filter == type
                card.contentDescription = if (filter == type) {
                    getString(R.string.manager_filter_on, text.type(type))
                } else {
                    text.type(type)
                }
            }
    }

    private fun pendingMessage(state: ManagerState): Int? = when {
        state.pending.isNotEmpty() -> null
        state.loading -> R.string.manager_loading
        state.listFailed -> R.string.manager_list_failed
        state.filter != null -> R.string.manager_pending_empty_filtered
        else -> R.string.manager_pending_empty
    }

    private fun decidedMessage(state: ManagerState): Int? = when {
        state.decided.isNotEmpty() || state.loading || state.listFailed -> null
        else -> R.string.manager_decided_empty
    }

    private fun fill(container: LinearLayout, actions: List<InterferenceAction>, text: RequestText, message: Int?) {
        container.removeAllViews()
        message?.let { container.addView(message(container, it)) }
        actions.forEach { container.addView(card(container, it, text)) }
    }

    private fun message(container: ViewGroup, message: Int): View = TextView(container.context).apply {
        setText(message)
        setTextColor(context.getColor(R.color.color_branco))
        alpha = MESSAGE_ALPHA
        textSize = MESSAGE_TEXT_SP
        val padding = (MESSAGE_PADDING_DP * resources.displayMetrics.density).toInt()
        setPadding(padding, padding, padding, padding)
        if (message == R.string.manager_list_failed) {
            isClickable = true
            isFocusable = true
            setOnClickListener { model.refresh() }
        }
    }

    private fun card(container: ViewGroup, action: InterferenceAction, text: RequestText): View {
        val item = ItemManagerRequestBinding.inflate(layoutInflater, container, false)
        val product = model.state.value.product(action)
        val title = text.title(action, product)
        item.managerRequestIcon.setImageResource(
            if (action.type ==
                ActionType.ORDER
            ) {
                R.drawable.box
            } else {
                R.drawable.promo
            },
        )
        item.managerRequestTitle.text = title
        item.managerRequestSubtitle.text = text.summary(action)
        val open = action.status.canDecide
        item.managerRequestAnalyze.isVisible = open
        item.managerRequestBadge.isVisible = !open
        if (!open) item.managerRequestBadge.showStatus(action.status)
        item.root.contentDescription = if (open) {
            getString(R.string.manager_analyze_description, text.type(action.type), title)
        } else {
            "$title, ${text.summary(action)}, ${text.status(action.status)}"
        }
        item.root.setOnClickListener { open(action) }
        return item.root
    }

    private fun open(action: InterferenceAction) {
        if (!model.select(action)) return
        findNavController().navigate(
            if (action.type == ActionType.ORDER) {
                R.id.action_managerInterferencesFragment_to_managerOrderDetailFragment
            } else {
                R.id.action_managerInterferencesFragment_to_managerPromotionDetailFragment
            },
        )
    }

    private companion object {
        const val MESSAGE_ALPHA = 0.78f
        const val MESSAGE_TEXT_SP = 12f
        const val MESSAGE_PADDING_DP = 8
    }
}
