package com.quistock.quistock.presentation.common

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.snackbar.Snackbar
import com.quistock.quistock.R

object PopupFeedback {
    private const val SNACKBAR_HORIZONTAL_PADDING = 16
    private const val DIALOG_WIDTH_RATIO = 0.82

    @JvmStatic
    fun showStatus(anchor: View, message: String, isError: Boolean) {
        val snackbar = Snackbar.make(anchor, "", Snackbar.LENGTH_LONG)
        val layout = LayoutInflater.from(anchor.context).inflate(R.layout.view_status_popup, null)
        val icon = layout.findViewById<ImageView>(R.id.statusPopupIcon)
        val text = layout.findViewById<TextView>(R.id.statusPopupMessage)
        layout.setBackgroundResource(if (isError) R.drawable.bg_popup_error else R.drawable.bg_popup_success)
        icon.setImageResource(if (isError) R.drawable.ic_error else R.drawable.ic_success)
        text.text = message
        val snackbarLayout = snackbar.view as Snackbar.SnackbarLayout
        snackbarLayout.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        snackbarLayout.setPadding(SNACKBAR_HORIZONTAL_PADDING, 0, SNACKBAR_HORIZONTAL_PADDING, 0)
        snackbarLayout.removeAllViews()
        snackbarLayout.addView(
            layout,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        snackbar.show()
    }

    @JvmStatic
    fun showConfirmation(context: Context, message: String, onConfirm: () -> Unit) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_confirmation_popup, null)
        view.findViewById<TextView>(R.id.confirmationMessage).text = message
        val dialog = AlertDialog.Builder(context).setView(view).create()
        view.findViewById<Button>(R.id.confirmationYes).setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }
        view.findViewById<Button>(R.id.confirmationNo).setOnClickListener { dialog.dismiss() }
        dialog.show()
        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT),
        )
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * DIALOG_WIDTH_RATIO).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    @JvmStatic
    fun showManagerOrderApproval(context: Context, onConfirm: () -> Unit) {
        showConfirmation(context, context.getString(R.string.popup_manager_approve_order), onConfirm)
    }

    @JvmStatic
    fun showManagerPromotionApproval(context: Context, onConfirm: () -> Unit) {
        showConfirmation(context, context.getString(R.string.popup_manager_approve_promotion), onConfirm)
    }

    @JvmStatic
    fun showNoConnection(context: Context, onRetry: () -> Unit) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_no_connection, null)
        val dialog = AlertDialog.Builder(context).setView(view).create()
        view.findViewById<View>(R.id.closeNoConnection).setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.retryNoConnection).setOnClickListener {
            dialog.dismiss()
            onRetry()
        }
        dialog.show()
        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT),
        )
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * DIALOG_WIDTH_RATIO).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
}
