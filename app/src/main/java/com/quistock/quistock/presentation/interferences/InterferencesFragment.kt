package com.quistock.quistock.presentation.interferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentInterferencesBinding
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.presentation.common.SkeletonLoadingScreen
import com.quistock.quistock.presentation.common.SkeletonScreenType
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class InterferencesFragment : Fragment() {
    private var binding: FragmentInterferencesBinding? = null
    private val model: CreateInterferenceViewModel by activityViewModel()
    private var dialog: AlertDialog? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val views = FragmentInterferencesBinding.inflate(inflater, container, false).also { binding = it }
        return SkeletonLoadingScreen.wrap(views.root, SkeletonScreenType.INTERFERENCES)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.cardPedidoEstoque?.setOnClickListener { openNew(ActionType.ORDER) }
        binding?.cardPromocao?.setOnClickListener { openNew(ActionType.PROMOTION) }
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
        binding = null
        super.onDestroyView()
    }

    private fun render(state: CreationState) {
        val views = binding ?: return
        val enabled = !state.busy && !state.loading
        views.cardPedidoEstoque.isEnabled = enabled
        views.cardPromocao.isEnabled = enabled
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
