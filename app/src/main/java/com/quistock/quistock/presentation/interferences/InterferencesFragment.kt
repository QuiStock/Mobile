package com.quistock.quistock.presentation.interferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentCreationHubBinding
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.InterferenceAction
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class InterferencesFragment : Fragment() {
    private var binding: FragmentCreationHubBinding? = null
    private val model: CreateInterferenceViewModel by activityViewModel()
    private var dialog: AlertDialog? = null
    private var renderedActions: Any? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentCreationHubBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding?.creationOrder?.setOnClickListener { openNew(ActionType.ORDER) }
        binding?.creationPromotion?.setOnClickListener { openNew(ActionType.PROMOTION) }
        binding?.creationResume?.setOnClickListener { open(model.state.value.type) }
        binding?.creationRetry?.setOnClickListener { model.refreshList() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect { render(it) } }
        }
        model.refreshList()
    }

    override fun onDestroyView() {
        dialog?.dismiss()
        dialog = null
        binding = null
        renderedActions = null
        super.onDestroyView()
    }

    private fun render(state: CreationState) {
        val views = binding ?: return
        views.creationResume.isVisible = model.hasDraft || state.action != null
        views.creationOrder.isEnabled = !state.busy && !state.loading
        views.creationPromotion.isEnabled = !state.busy && !state.loading
        views.creationRetry.isEnabled = !state.busy && !state.loading
        views.creationMessage.setText(
            when {
                state.loading -> R.string.creation_loading
                state.listFailed -> R.string.creation_list_error
                state.actions.isEmpty() -> R.string.creation_no_actions
                else -> R.string.creation_advisory
            },
        )
        if (renderedActions === state.actions) return
        renderedActions = state.actions
        views.creationActions.removeAllViews()
        state.actions.forEach { action ->
            views.creationActions.addView(
                Button(requireContext()).apply {
                    text = CreationPresentation.action(context, action)
                    isAllCaps = false
                    setOnClickListener {
                        openExisting(action)
                    }
                },
            )
        }
    }

    private fun openExisting(action: InterferenceAction) {
        val proceed = {
            model.showExisting(action)
            open(action.type)
        }
        if (model.hasDraft) {
            dialog = AlertDialog.Builder(requireContext()).setMessage(R.string.creation_discard)
                .setPositiveButton(R.string.creation_discard_yes) { _, _ -> proceed() }
                .setNegativeButton(R.string.creation_cancel, null).show()
        } else {
            proceed()
        }
    }

    private fun openNew(type: ActionType) {
        if (!model.hasDraft && model.state.value.action == null) {
            open(type)
            return
        }
        dialog = AlertDialog.Builder(requireContext()).setMessage(R.string.creation_discard)
            .setPositiveButton(R.string.creation_discard_yes) { _, _ ->
                model.discard()
                open(type)
            }
            .setNegativeButton(R.string.creation_cancel, null).show()
    }

    private fun open(type: ActionType) {
        findNavController().navigate(if (type == ActionType.ORDER) R.id.orderFragment else R.id.promotionFragment)
    }
}
