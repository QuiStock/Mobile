package com.quistock.quistock.presentation.profile

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.quistock.quistock.R
import com.quistock.quistock.domain.model.UserProfile
import com.quistock.quistock.domain.model.UserStatus

/** Read-only dialogs for the profile rows "Minha conta" and "Minha loja". */
internal object ProfileDetailsDialog {
    fun showAccount(context: Context, profile: UserProfile) = show(
        context,
        R.string.profile_account,
        listOf(
            R.string.profile_details_name to profile.name,
            R.string.profile_details_email to profile.email,
            R.string.profile_details_role to profile.role.displayName,
            R.string.profile_details_status to context.getString(
                if (profile.status == UserStatus.ACTIVE) {
                    R.string.profile_details_status_active
                } else {
                    R.string.profile_details_status_inactive
                },
            ),
        ),
    )

    fun showStore(context: Context, profile: UserProfile) = show(
        context,
        R.string.profile_store,
        listOf(
            R.string.profile_details_store_name to profile.storeName,
            R.string.profile_details_store_code to profile.storeCode,
            R.string.profile_details_region to (profile.regionName ?: profile.regionCode),
        ),
    )

    private fun show(context: Context, title: Int, fields: List<Pair<Int, String?>>) {
        val message = fields.joinToString("\n\n") { (label, value) ->
            "${context.getString(label)}\n${value?.takeIf {
                it.isNotBlank()
            } ?: context.getString(R.string.profile_details_unavailable)}"
        }
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.profile_details_close, null)
            .show()
    }
}
