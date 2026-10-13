package com.quistock.quistock.presentation.profile

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.quistock.quistock.R

class ProfileFragment : Fragment(R.layout.fragment_profile) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.profile_help_row).setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_faqFragment)
        }
    }
}
