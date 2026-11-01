package com.quistock.quistock.presentation.interferences

import android.graphics.Color
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.quistock.quistock.R
import com.quistock.quistock.domain.model.ActionDecision
import com.quistock.quistock.domain.model.ActionFailure
import com.quistock.quistock.domain.model.Justification

/** Motion and haptics for decisions. Animator durations follow the system animation scale. */
internal object DecisionMotion {
    private const val BADGE_START_SCALE = 0.6f
    private const val BADGE_DURATION_MS = 320L
    private const val OVERSHOOT_TENSION = 2.2f
    private const val SHAKE_DURATION_MS = 60L
    private const val SHAKE_OFFSET_DP = 6

    fun haptic(view: View, decision: ActionDecision) {
        val constant = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> HapticFeedbackConstants.VIRTUAL_KEY
            decision == ActionDecision.APPROVE -> HapticFeedbackConstants.CONFIRM
            else -> HapticFeedbackConstants.REJECT
        }
        view.performHapticFeedback(constant)
    }

    /** The one authored moment: the status pill settles in when a decision is confirmed. */
    fun revealBadge(badge: View) {
        badge.scaleX = BADGE_START_SCALE
        badge.scaleY = BADGE_START_SCALE
        badge.alpha = 0f
        badge.animate().scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(BADGE_DURATION_MS)
            .setInterpolator(OvershootInterpolator(OVERSHOOT_TENSION))
            .start()
    }

    fun shake(view: View) {
        val offset = SHAKE_OFFSET_DP * view.resources.displayMetrics.density
        view.animate().translationX(offset).setDuration(SHAKE_DURATION_MS).withEndAction {
            view.animate().translationX(-offset).setDuration(SHAKE_DURATION_MS).withEndAction {
                view.animate().translationX(0f).setDuration(SHAKE_DURATION_MS).start()
            }.start()
        }.start()
    }
}

/** Bottom sheet that collects the mandatory rejection justification. */
internal object JustificationSheet {
    fun show(anchor: View, subject: String, onConfirm: (String) -> Unit): BottomSheetDialog {
        val context = anchor.context
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.sheet_decision_justification, null)
        val input = view.findViewById<EditText>(R.id.decisionSheetInput)
        val error = view.findViewById<TextView>(R.id.decisionSheetError)
        view.findViewById<TextView>(R.id.decisionSheetProduct).text = subject
        input.doAfterTextChanged { if (!it.isNullOrBlank()) error.isVisible = false }
        view.findViewById<Button>(R.id.decisionSheetCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.decisionSheetConfirm).setOnClickListener { button ->
            when (val text = ActionDecision.REJECT.justification(input.text.toString())) {
                is Justification.Valid -> {
                    DecisionMotion.haptic(button, ActionDecision.REJECT)
                    dialog.dismiss()
                    onConfirm(requireNotNull(text.text))
                }

                Justification.Missing -> {
                    error.isVisible = true
                    input.requestFocus()
                    DecisionMotion.shake(input)
                }
            }
        }
        dialog.setContentView(view)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.setOnShowListener {
            // Let the sheet's own rounded surface show instead of the default container.
            (view.parent as? View)?.background = Color.TRANSPARENT.toDrawable()
            input.requestFocus()
        }
        dialog.show()
        return dialog
    }
}

/** Failure copy names the problem and the recovery; technical messages are never shown. */
internal object DecisionMessages {
    fun failure(failure: ActionFailure?): Int = when (failure) {
        ActionFailure.CONFLICT -> R.string.manager_feedback_conflict
        ActionFailure.UNCERTAIN -> R.string.manager_feedback_uncertain
        ActionFailure.FORBIDDEN -> R.string.manager_feedback_forbidden
        ActionFailure.NOT_FOUND -> R.string.manager_feedback_not_found
        ActionFailure.UNAUTHORIZED -> R.string.manager_feedback_unauthorized
        ActionFailure.INVALID -> R.string.manager_feedback_invalid
        else -> R.string.manager_feedback_failed
    }
}
