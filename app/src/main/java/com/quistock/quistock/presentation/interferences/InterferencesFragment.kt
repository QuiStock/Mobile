package com.quistock.quistock.presentation.interferences

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentInterferencesBinding
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.InterferenceAction
import com.quistock.quistock.presentation.common.SkeletonLoadingScreen
import com.quistock.quistock.presentation.common.SkeletonScreenType
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class InterferencesFragment : Fragment() {
    private var binding: FragmentInterferencesBinding? = null
    private val model: CreateInterferenceViewModel by activityViewModel()
    private var dialog: AlertDialog? = null
    private var renderedActions: Pair<List<InterferenceAction>, List<InterferenceAction>>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val views = FragmentInterferencesBinding.inflate(inflater, container, false).also { binding = it }
        return SkeletonLoadingScreen.wrap(views.root, SkeletonScreenType.INTERFERENCES)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.cardPedidoEstoque?.setOnClickListener { openNew(ActionType.ORDER) }
        binding?.cardPromocao?.setOnClickListener { openNew(ActionType.PROMOTION) }
        binding?.interferenceListRetry?.setOnClickListener { model.refreshList() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { render(it) }
            }
        }
        model.refreshList()
    }

    override fun onDestroyView() {
        dialog?.dismiss()
        dialog = null
        renderedActions = null
        binding = null
        super.onDestroyView()
    }

    private fun render(state: CreationState) {
        val views = binding ?: return
        val enabled = !state.busy && !state.loading
        views.cardPedidoEstoque.isEnabled = enabled
        views.cardPromocao.isEnabled = enabled
        views.interferenceListProgress.isVisible = state.loading
        val message = when {
            state.loading -> R.string.creation_actions_loading
            state.listFailed -> R.string.creation_list_error
            state.actions.isEmpty() -> R.string.creation_no_actions
            else -> null
        }
        views.interferenceListMessage.isVisible = message != null
        message?.let { views.interferenceListMessage.setText(it) }
        views.interferenceListRetry.isVisible = state.listFailed && !state.loading
        val active = state.actions.filter { it.status.pending }
        val recent = state.actions
        views.listaInterferenciasAtivas.isVisible = active.isNotEmpty() && !state.listFailed
        views.listaInterferenciasRecentes.isVisible = recent.isNotEmpty() && !state.listFailed
        renderActions(active, recent)
    }

    private fun renderActions(active: List<InterferenceAction>, recent: List<InterferenceAction>) {
        val views = binding ?: return
        val actions = active to recent
        if (renderedActions == actions) return
        renderedActions = actions
        renderActions(views.listaInterferenciasAtivas, active, withIcon = true)
        renderActions(views.listaInterferenciasRecentes, recent, withIcon = false)
    }

    private fun renderActions(container: LinearLayout, actions: List<InterferenceAction>, withIcon: Boolean) {
        container.removeAllViews()
        actions.forEach { action ->
            container.addView(
                InterferenceActionRows.create(requireContext(), action, withIcon) {
                    model.showExisting(action)
                    if (model.state.value.action == action) open(action.type)
                },
                InterferenceActionRows.layoutParams(requireContext()),
            )
        }
    }

    private fun openNew(type: ActionType) {
        if (!model.hasDraft && model.state.value.action == null) {
            open(type)
            return
        }
        dialog = AlertDialog.Builder(requireContext())
            .setMessage(R.string.creation_discard)
            .setPositiveButton(R.string.creation_discard_yes) { _, _ ->
                model.discard()
                open(type)
            }
            .setNegativeButton(R.string.creation_cancel, null)
            .show()
    }

    private fun open(type: ActionType) {
        findNavController().navigate(if (type == ActionType.ORDER) R.id.orderFragment else R.id.promotionFragment)
    }
}

private object InterferenceActionRows {
    fun create(context: Context, action: InterferenceAction, withIcon: Boolean, onClick: () -> Unit) =
        LinearLayout(context).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            minimumHeight = dp(context, CARD_MIN_HEIGHT)
            setPadding(
                dp(context, if (withIcon) ACTIVE_CARD_START_PADDING else CARD_START_PADDING),
                dp(context, CARD_VERTICAL_PADDING),
                dp(context, CARD_END_PADDING),
                dp(context, CARD_VERTICAL_PADDING),
            )
            setBackgroundResource(R.drawable.bg_card_interference_history)
            isClickable = true
            isFocusable = true
            contentDescription = CreationActionPresentation.action(context, action, action.productName)
            setOnClickListener { onClick() }
            if (withIcon) addView(actionIcon(context, action), iconLayoutParams(context))
            addView(actionText(context, action), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

    fun layoutParams(context: Context) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { bottomMargin = dp(context, CARD_BOTTOM_MARGIN) }

    private fun actionIcon(context: Context, action: InterferenceAction) = ImageView(context).apply {
        setImageResource(if (action.type == ActionType.PROMOTION) R.drawable.promo else R.drawable.box)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun actionText(context: Context, action: InterferenceAction) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(
            TextView(context).apply {
                text = CreationActionPresentation.actionTitle(context, action, action.productName)
                setTextColor(context.resources.getColor(R.color.color_branco, null))
                textSize = ACTION_TITLE_TEXT_SIZE
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            },
        )
        addView(
            TextView(context).apply {
                text = CreationActionPresentation.actionSubtitle(context, action)
                setTextColor(ACTION_SUBTITLE_COLOR)
                textSize = ACTION_SUBTITLE_TEXT_SIZE
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(context, SUBTITLE_TOP_MARGIN) },
        )
    }

    private fun iconLayoutParams(context: Context) =
        LinearLayout.LayoutParams(dp(context, ICON_SIZE), dp(context, ICON_SIZE)).apply {
            marginEnd = dp(context, ICON_END_MARGIN)
        }

    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private const val CARD_MIN_HEIGHT = 62
    private const val ACTIVE_CARD_START_PADDING = 20
    private const val CARD_START_PADDING = 14
    private const val CARD_END_PADDING = 14
    private const val CARD_VERTICAL_PADDING = 10
    private const val ICON_SIZE = 32
    private const val ICON_END_MARGIN = 15
    private const val ACTION_TITLE_TEXT_SIZE = 14f
    private const val ACTION_SUBTITLE_TEXT_SIZE = 10f
    private const val SUBTITLE_TOP_MARGIN = 3
    private const val CARD_BOTTOM_MARGIN = 8
    private const val ACTION_SUBTITLE_COLOR = 0xFFBFAFC5.toInt()
}
