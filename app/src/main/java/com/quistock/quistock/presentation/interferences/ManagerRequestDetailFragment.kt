package com.quistock.quistock.presentation.interferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.viewbinding.ViewBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.quistock.quistock.R
import com.quistock.quistock.domain.model.ActionDecision
import com.quistock.quistock.domain.model.ActionStatus
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.InterferenceAction
import com.quistock.quistock.presentation.common.PopupFeedback
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/** Views shared by the order and promotion review screens, with their state rendering. */
internal class DetailViews(
    val name: TextView,
    val flow: TextView,
    val category: TextView,
    val expiry: TextView,
    val expiryHint: TextView,
    val status: TextView,
    val reason: TextView,
    val decisionCard: View,
    val decisionLabel: TextView,
    val decisionText: TextView,
    val approve: Button,
    val reject: Button,
    val back: View,
) {
    fun showProduct(product: AlertProduct?) {
        val text = ProductText(name.context)
        flow.text = text.flow(product?.flow)
        flow.isVisible = product?.flow != null
        category.text = text.categoryStock(product)
        expiry.text = text.expiry(product)
        expiryHint.isVisible = false
    }

    /** Status pill and locked decision card; animates only the pending-to-final transition. */
    fun showStatus(action: InterferenceAction, previous: ActionStatus?) {
        status.showStatus(action.status)
        if (previous?.canDecide == true && action.status.decided) DecisionMotion.revealBadge(status)
        decisionCard.isVisible = action.status.decided
        val justification = action.decisionJustification
        decisionLabel.isVisible = justification != null
        decisionText.isVisible = justification != null
        decisionText.text = justification
    }

    fun showActions(action: InterferenceAction, state: ManagerState) {
        val open = action.status.canDecide
        val inFlight = state.submitting
        approve.isVisible = open
        reject.isVisible = open
        approve.isEnabled = open && !inFlight
        reject.isEnabled = open && !inFlight
        val approving = inFlight && state.decision == ActionDecision.APPROVE
        val rejecting = inFlight && state.decision == ActionDecision.REJECT
        approve.setText(if (approving) R.string.manager_approving else R.string.manager_approve)
        reject.setText(if (rejecting) R.string.manager_rejecting else R.string.manager_reject)
    }

    fun showUnavailable() {
        name.setText(R.string.manager_unavailable)
        approve.isVisible = false
        reject.isVisible = false
        status.isVisible = false
        decisionCard.isVisible = false
    }
}

abstract class ManagerRequestDetailFragment<B : ViewBinding> : Fragment() {
    private var binding: B? = null
    private val model: ManagerInterferencesViewModel by activityViewModel()
    private var sheet: BottomSheetDialog? = null
    private var shownStatus: ActionStatus? = null

    internal abstract val type: ActionType

    @get:IdRes internal abstract val approvedRoute: Int

    internal abstract fun inflate(inflater: LayoutInflater, container: ViewGroup?): B
    internal abstract fun views(binding: B): DetailViews
    internal abstract fun bindValues(binding: B, action: InterferenceAction, text: RequestText)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflate(inflater, container).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = views(requireNotNull(binding))
        views.back.setOnClickListener { findNavController().popBackStack() }
        views.approve.setOnClickListener { confirmApproval(it) }
        views.reject.setOnClickListener { askJustification(it) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { render(it) }
            }
        }
    }

    override fun onDestroyView() {
        sheet?.dismiss()
        sheet = null
        binding = null
        shownStatus = null
        super.onDestroyView()
    }

    private fun confirmApproval(button: View) {
        if (model.state.value.submitting) return
        val confirm = {
            DecisionMotion.haptic(button, ActionDecision.APPROVE)
            model.decide(ActionDecision.APPROVE)
        }
        if (type == ActionType.ORDER) {
            PopupFeedback.showManagerOrderApproval(requireContext(), confirm)
        } else {
            PopupFeedback.showManagerPromotionApproval(requireContext(), confirm)
        }
    }

    private fun askJustification(button: View) {
        val action = model.state.value.selected ?: return
        if (model.state.value.submitting || sheet?.isShowing == true) return
        val text = RequestText(requireContext())
        val subject = text.title(action, model.state.value.product(action)) + " · " + text.type(action.type)
        sheet = JustificationSheet.show(button, subject) { model.decide(ActionDecision.REJECT, it) }
    }

    private fun render(state: ManagerState) {
        val binding = binding ?: return
        val views = views(binding)
        val action = state.selected?.takeIf { it.type == type }
        if (action == null) {
            views.showUnavailable()
            return
        }
        val text = RequestText(requireContext())
        views.name.text = text.title(action, state.product(action))
        views.showProduct(state.product(action))
        views.reason.text = text.reason(action)
        bindValues(binding, action, text)
        views.showStatus(action, shownStatus)
        shownStatus = action.status
        views.showActions(action, state)
        consume(views, state)
    }

    /** One-shot feedback; acknowledged so returning to the screen never replays it. */
    private fun consume(views: DetailViews, state: ManagerState) {
        when (state.phase) {
            DecisionPhase.DONE -> {
                model.acknowledge()
                if (state.decision == ActionDecision.APPROVE) {
                    findNavController().navigate(approvedRoute)
                } else {
                    val message = getString(R.string.manager_feedback_rejected)
                    PopupFeedback.showStatus(views.status, message, false)
                }
            }

            DecisionPhase.FAILED -> {
                model.acknowledge()
                PopupFeedback.showStatus(views.status, getString(DecisionMessages.failure(state.failure)), true)
            }

            else -> Unit
        }
    }
}
