package com.quistock.quistock.presentation.interferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentCreationBinding
import com.quistock.quistock.domain.model.ActionFailure
import com.quistock.quistock.domain.model.ActionType
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel

abstract class CreationFragment(private val type: ActionType) : Fragment() {
    private var binding: FragmentCreationBinding? = null
    private val model: CreateInterferenceViewModel by activityViewModel()
    private var fields: CreationFields? = null
    private var dialog: AlertDialog? = null
    private var renderedProducts: Any? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentCreationBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val views = requireNotNull(binding)
        fields = CreationFields(views, model)
        views.creationTitle.setText(CreationPresentation.type(type))
        views.creationMessage.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        views.creationBack.setOnClickListener { back() }
        views.creationSubmit.setOnClickListener {
            model.review()
            fields?.focusError(model.state.value)
        }
        views.creationForward.setOnClickListener {
            val action = model.state.value.action ?: return@setOnClickListener
            dialog = AlertDialog.Builder(requireContext())
                .setMessage(CreationPresentation.action(requireContext(), action))
                .setPositiveButton(R.string.creation_confirm) { _, _ -> model.forward() }
                .setNegativeButton(R.string.creation_cancel, null).show()
        }
        views.creationRetry.setOnClickListener { model.load(type) }
        views.creationReturn.setOnClickListener { returnToHub() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = back()
        })
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { render(it) }
            }
        }
        model.enter(type)
    }

    override fun onDestroyView() {
        dialog?.dismiss()
        dialog = null
        fields = null
        binding = null
        renderedProducts = null
        super.onDestroyView()
    }

    private fun render(state: CreationState) {
        val views = binding ?: return
        fields?.render(state)
        views.creationTitle.setText(CreationPresentation.type(state.type))
        views.creationProgress.isVisible = state.loading || state.busy
        views.creationBack.isEnabled = !state.busy
        views.creationSubmit.isVisible = state.phase == CreationPhase.FORM
        views.creationForward.isVisible = state.phase == CreationPhase.EXISTING &&
            state.action?.status?.canForward == true && state.failure !in setOf(
                ActionFailure.FORBIDDEN, ActionFailure.NOT_FOUND, ActionFailure.CONFLICT,
            )
        views.creationRetry.isVisible = !state.busy && state.phase != CreationPhase.REVIEW &&
            state.phase != CreationPhase.SENT && !state.mustCheckList
        views.creationReturn.isVisible = !state.busy && state.phase in setOf(
            CreationPhase.SENT, CreationPhase.ERROR, CreationPhase.EXISTING, CreationPhase.EMPTY,
        )
        views.creationProduct.text = state.product?.let { CreationPresentation.product(requireContext(), it) }.orEmpty()
        views.creationSummary.text = state.action?.let { CreationPresentation.action(requireContext(), it) }.orEmpty()
        views.creationMessage.setText(message(state))
        renderProducts(state)
        if (state.phase == CreationPhase.REVIEW && dialog?.isShowing != true) showReview(state)
    }

    private fun renderProducts(state: CreationState) {
        val views = binding ?: return
        views.creationProducts.isVisible = state.phase == CreationPhase.SELECT && !state.loading
        if (renderedProducts === state.products) return
        renderedProducts = state.products
        views.creationProducts.removeAllViews()
        state.products.forEach { product ->
            views.creationProducts.addView(Button(requireContext()).apply {
                text = CreationPresentation.product(context, product)
                isAllCaps = false
                setOnClickListener { model.select(product) }
            })
        }
    }

    private fun message(state: CreationState): Int = when {
        state.busy -> R.string.creation_sending
        state.loading -> R.string.creation_loading
        state.failure != null -> CreationPresentation.failure(state.failure)
        state.phase == CreationPhase.SENT -> R.string.creation_sent
        state.phase == CreationPhase.EXISTING -> R.string.creation_existing
        state.phase == CreationPhase.EMPTY -> R.string.creation_no_generation
        state.phase == CreationPhase.SELECT && state.products.isEmpty() -> R.string.creation_empty
        else -> R.string.creation_select
    }

    private fun showReview(state: CreationState) {
        val product = state.product ?: return
        val summary = getString(CreationPresentation.type(state.type)) + "\n" + product.name + "\n" +
            CreationPresentation.values(requireContext(), state.type, state.draft)
        dialog = AlertDialog.Builder(requireContext()).setMessage(summary)
            .setPositiveButton(R.string.creation_confirm) { _, _ -> model.confirm() }
            .setNegativeButton(R.string.creation_cancel) { _, _ -> model.review(cancel = true) }
            .setOnCancelListener { model.review(cancel = true) }.show()
    }

    private fun back() {
        if (model.state.value.busy) return
        if (model.state.value.phase == CreationPhase.SENT || model.state.value.mustCheckList) {
            returnToHub()
            return
        }
        if (!model.hasDraft) {
            findNavController().popBackStack()
            return
        }
        dialog = AlertDialog.Builder(requireContext()).setMessage(R.string.creation_discard)
            .setPositiveButton(R.string.creation_discard_yes) { _, _ ->
                model.discard()
                findNavController().popBackStack()
            }.setNegativeButton(R.string.creation_cancel, null).show()
    }

    private fun returnToHub() {
        model.discard()
        findNavController().navigate(
            R.id.interferencesFragment, null,
            navOptions {
                popUpTo(R.id.nav_graph) { inclusive = false }
                launchSingleTop = true
            },
        )
    }
}
