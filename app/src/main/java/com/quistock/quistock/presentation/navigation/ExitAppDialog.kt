package com.quistock.quistock.presentation.navigation

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.quistock.quistock.R

class ExitAppDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = AlertDialog.Builder(requireContext())
        .setTitle(R.string.navigation_exit_title)
        .setMessage(R.string.navigation_exit_message)
        .setNegativeButton(R.string.navigation_exit_cancel, null)
        .setPositiveButton(R.string.navigation_exit_confirm) { _, _ -> requireActivity().finish() }
        .create()

    companion object {
        const val TAG = "exit_application"

        fun hide(manager: FragmentManager) {
            (manager.findFragmentByTag(TAG) as? ExitAppDialog)?.dismissAllowingStateLoss()
        }
    }
}
