package com.quistock.quistock.presentation.profile

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.quistock.quistock.R
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.presentation.activity.SessionViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class LogoutConfirmationDialog : DialogFragment() {
    private val viewModel: SessionViewModel by activityViewModel()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = AlertDialog.Builder(requireContext())
        .setTitle(R.string.logout_confirmation_title)
        .setMessage(R.string.logout_confirmation_message)
        .setNegativeButton(R.string.logout_cancel, null)
        .setPositiveButton(R.string.logout_confirm) { _, _ -> viewModel.logout() }
        .create()

    override fun onStart() {
        super.onStart()
        (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            isFocusableInTouchMode = true
            requestFocus()
            sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED)
        }
    }

    companion object {
        const val TAG = "logout_confirmation"
    }
}

/** Activity-owned so no authenticated destination remains usable during local recovery. */
class LogoutProgressDialog : DialogFragment() {
    private val viewModel: SessionViewModel by activityViewModel()
    private var renderingJob: Job? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = false
        return AlertDialog.Builder(requireContext())
            .setMessage(R.string.logout_progress)
            .setPositiveButton(R.string.logout_retry, null)
            .create()
    }

    override fun onStart() {
        super.onStart()
        (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            viewModel.retryLogoutCleanup()
        }
        renderingJob?.cancel()
        renderingJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.session.state.collect { state ->
                    val alert = dialog as? AlertDialog ?: return@collect
                    val failed = state is SessionState.LogoutFailed
                    alert.getButton(AlertDialog.BUTTON_POSITIVE).visibility = if (failed) View.VISIBLE else View.GONE
                    alert.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = failed
                    val message = getString(if (failed) R.string.logout_local_failure else R.string.logout_progress)
                    alert.setMessage(message)
                    alert.findViewById<View>(android.R.id.message)?.apply {
                        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
                        isFocusableInTouchMode = true
                        requestFocus()
                        announceForAccessibility(message)
                    }
                }
            }
        }
    }

    override fun onStop() {
        renderingJob?.cancel()
        renderingJob = null
        super.onStop()
    }

    companion object {
        const val TAG = "logout_progress"
    }
}

class LogoutRemoteWarningDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = AlertDialog.Builder(requireContext())
        .setTitle(R.string.logout_remote_warning_title)
        .setMessage(R.string.logout_remote_warning_message)
        .setPositiveButton(R.string.logout_understood, null)
        .create()

    override fun onStart() {
        super.onStart()
        (dialog as? AlertDialog)?.findViewById<View>(androidx.appcompat.R.id.alertTitle)?.apply {
            isFocusableInTouchMode = true
            requestFocus()
            announceForAccessibility(getString(R.string.logout_remote_warning_message))
        }
    }

    companion object {
        const val TAG = "logout_remote_warning"
        const val ARGUMENT = "logout_remote_unconfirmed"
    }
}
