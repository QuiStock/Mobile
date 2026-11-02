package com.quistock.quistock.presentation.interferences

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
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

private const val PRODUCT_CARD_TEXT_SIZE_SP = 14f
private const val PRODUCT_CARD_HORIZONTAL_PADDING_DP = 18
private const val PRODUCT_CARD_VERTICAL_PADDING_DP = 14

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
        }
        views.creationForward.setOnClickListener {
            val action = model.state.value.action ?: return@setOnClickListener
            dialog = AlertDialog.Builder(requireContext())
                .setMessage(CreationActionPresentation.action(requireContext(), action))
                .setPositiveButton(R.string.creation_confirm) { _, _ -> model.forward() }
                .setNegativeButton(R.string.creation_cancel, null).show()
        }
        views.creationRetry.setOnClickListener {
            if (model.state.value.phase == CreationPhase.EXISTING) model.refreshExisting() else model.load()
        }
        views.creationReturn.setOnClickListener { returnToHub() }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = back()
            },
        )
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
        CreationFragmentRendering.renderVisibility(views, state)
        CreationFragmentRendering.renderResult(requireContext(), views, state)
        views.creationProgress.isVisible = state.loading || state.busy
        renderActions(views, state)
        CreationFragmentRendering.renderSummary(requireContext(), views, state)
        CreationFragmentRendering.renderMessage(requireContext(), views, state)
        views.creationProgress.contentDescription = getString(
            if (state.busy) R.string.creation_sending else R.string.creation_loading,
        )
        renderProducts(state)
        views.creationProducts.isVisible = state.phase == CreationPhase.SELECT && !state.loading
        if (state.phase == CreationPhase.REVIEW && dialog?.isShowing != true) showReview(state)
    }

    private fun renderActions(views: FragmentCreationBinding, state: CreationState) {
        views.creationBack.isEnabled = !state.busy
        views.creationSubmit.isVisible = state.phase == CreationPhase.FORM
        views.creationSubmit.isEnabled = !state.loading && !state.listFailed &&
            state.failure != ActionFailure.UNAUTHORIZED
        views.creationRetry.isEnabled = !state.loading
        views.creationRetry.setText(
            if (state.phase == CreationPhase.EXISTING) R.string.creation_refresh_action else R.string.creation_retry,
        )
        views.creationForward.isVisible = state.phase == CreationPhase.EXISTING &&
            state.action?.status?.canForward == true && state.failure !in setOf(
                ActionFailure.FORBIDDEN,
                ActionFailure.NOT_FOUND,
                ActionFailure.CONFLICT,
                ActionFailure.UNAUTHORIZED,
            )
        views.creationRetry.isVisible = !state.busy && state.phase != CreationPhase.REVIEW &&
            state.phase != CreationPhase.SENT && !state.mustCheckList
        views.creationReturn.isVisible = !state.busy && state.phase in setOf(
            CreationPhase.SENT,
            CreationPhase.ERROR,
            CreationPhase.EXISTING,
            CreationPhase.EMPTY,
        )
    }

    private fun renderProducts(state: CreationState) {
        val views = binding ?: return
        views.creationProducts.isVisible = state.phase == CreationPhase.SELECT && !state.loading
        if (renderedProducts === state.products) return
        renderedProducts = state.products
        views.creationProducts.removeAllViews()
        state.products.forEach { product ->
            views.creationProducts.addView(
                Button(requireContext()).apply {
                    text = CreationCardFormatter.formatProductCard(context, product)
                    isAllCaps = false
                    background = ContextCompat.getDrawable(context, R.drawable.bg_card_summary)
                    setTextColor(ContextCompat.getColor(context, R.color.color_text_title))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, PRODUCT_CARD_TEXT_SIZE_SP)
                    val horizontalPadding = dp(PRODUCT_CARD_HORIZONTAL_PADDING_DP)
                    val verticalPadding = dp(PRODUCT_CARD_VERTICAL_PADDING_DP)
                    setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    contentDescription = text
                    setOnClickListener { model.select(product) }
                },
                android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(PRODUCT_BOTTOM_MARGIN) },
            )
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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
        when {
            model.state.value.busy -> Unit

            model.state.value.phase == CreationPhase.SENT || model.state.value.mustCheckList -> returnToHub()

            !model.hasDraft -> findNavController().popBackStack()

            else -> {
                dialog = AlertDialog.Builder(requireContext()).setMessage(R.string.creation_discard)
                    .setPositiveButton(R.string.creation_discard_yes) { _, _ ->
                        model.discard()
                        findNavController().popBackStack()
                    }.setNegativeButton(R.string.creation_cancel, null).show()
            }
        }
    }

    private fun returnToHub() {
        model.discard()
        findNavController().navigate(
            R.id.interferencesFragment,
            null,
            navOptions {
                popUpTo(R.id.nav_graph) { inclusive = false }
                launchSingleTop = true
            },
        )
    }

    private companion object {
        const val PRODUCT_BOTTOM_MARGIN = 8
    }
}

private object CreationFragmentRendering {
    fun renderVisibility(views: FragmentCreationBinding, state: CreationState) {
        val sent = state.phase == CreationPhase.SENT
        val result = state.phase in RESULT_PHASES
        views.creationHeaderContainer.isVisible = !sent
        views.creationBackContainer.isVisible = !sent
        views.creationTitle.isVisible = !sent
        views.creationAdvisory.isVisible = !sent
        views.creationMessage.isVisible = !sent
        views.creationProduct.isVisible = state.product != null && !result && state.phase != CreationPhase.SELECT
        views.creationSummary.isVisible = state.action != null && !sent
        views.creationSentTopSpace.isVisible = sent
        views.creationSentBottomSpace.isVisible = sent
        views.creationSentContainer.isVisible = sent
    }

    fun renderResult(context: Context, views: FragmentCreationBinding, state: CreationState) {
        val sent = state.phase == CreationPhase.SENT
        if (sent) renderSent(context, views, state)
        views.creationReturn.setText(R.string.creation_return)
        val notice = CreationPresentation.existingNotice(state)
        if (!sent) {
            views.creationMessage.text = notice?.let { existingMessage(context, it, state) }
                ?: context.getString(message(state))
        }
    }

    private fun renderSent(context: Context, views: FragmentCreationBinding, state: CreationState) {
        val isOrder = state.type == ActionType.ORDER
        views.creationSentTitle.setText(
            if (isOrder) R.string.creation_order_sent_title else R.string.creation_promotion_sent_title,
        )
        views.creationSentSubtitle.setText(
            if (isOrder) R.string.creation_order_sent_subtitle else R.string.creation_promotion_sent_subtitle,
        )
        val productText = state.product?.let { CreationPresentation.product(context, it) }.orEmpty()
        val summaryText = state.action?.let {
            CreationActionPresentation.action(context, it, state.product?.name ?: it.productName)
        }.orEmpty()
        views.creationSentDetailsText.text = CreationCardFormatter.formatSentDetails(summaryText, productText)
    }

    private fun existingMessage(context: Context, notice: Int, state: CreationState): String {
        val draft = state.requestedDraft ?: return context.getString(notice)
        val requested = CreationPresentation.values(context, state.type, draft)
        return if (state.loading || state.failure != null) {
            context.getString(notice) + "\n" + context.getString(R.string.creation_requested_values, requested)
        } else {
            context.getString(notice, requested)
        }
    }

    fun renderSummary(context: Context, views: FragmentCreationBinding, state: CreationState) {
        views.creationProduct.text =
            state.product?.let { CreationCardFormatter.formatProductCard(context, it) } ?: ""
        views.creationSummary.text = state.action?.let {
            CreationActionPresentation.action(context, it, state.product?.name ?: it.productName)
        }.orEmpty()
    }

    fun renderMessage(context: Context, views: FragmentCreationBinding, state: CreationState) {
        val text = context.getString(message(state))
        if (views.creationMessage.text.toString() != text) views.creationMessage.text = text
    }

    private fun message(state: CreationState): Int = when {
        state.busy -> R.string.creation_sending

        state.loading && state.phase == CreationPhase.EXISTING -> R.string.creation_action_refreshing

        state.loading -> R.string.creation_loading

        state.phase == CreationPhase.SELECT && state.listFailed -> R.string.creation_selection_list_error

        state.phase == CreationPhase.EXISTING && state.failure == ActionFailure.READ ->
            R.string.creation_action_refresh_error

        state.phase == CreationPhase.EXISTING && state.failure == ActionFailure.NOT_FOUND ->
            R.string.creation_action_missing

        state.failure != null -> CreationPresentation.failure(state.failure)

        else -> phaseMessage(state)
    }

    private fun phaseMessage(state: CreationState): Int = when {
        state.phase == CreationPhase.SENT -> R.string.creation_sent
        state.phase == CreationPhase.EXISTING -> R.string.creation_existing
        state.phase == CreationPhase.EMPTY -> R.string.creation_no_generation
        state.phase == CreationPhase.SELECT && state.products.isEmpty() -> R.string.creation_empty
        else -> R.string.creation_select
    }

    private val RESULT_PHASES = setOf(
        CreationPhase.SENT,
        CreationPhase.EXISTING,
        CreationPhase.EMPTY,
        CreationPhase.ERROR,
    )
}
