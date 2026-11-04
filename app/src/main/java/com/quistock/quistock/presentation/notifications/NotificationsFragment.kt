package com.quistock.quistock.presentation.notifications

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentNotificationsBinding

class NotificationsFragment : Fragment(R.layout.fragment_notifications) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        FragmentNotificationsBinding.bind(view).notificationsBackControl.setOnClickListener {
            // The bell exists on every tab, so back returns to wherever it was tapped. The check
            // keeps a repeated tap during the transition from also popping the previous screen.
            val nav = findNavController()
            if (nav.currentDestination?.id == R.id.notificationsFragment) nav.popBackStack()
        }
    }
}
