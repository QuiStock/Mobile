package com.quistock.quistock.presentation.onboarding

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.quistock.quistock.R

class OnboardingFragment : Fragment(R.layout.fragment_onboarding) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.onboarding_continue_button)?.setOnClickListener {
            if (findNavController().currentDestination?.id == R.id.onboardingFragment) {
                findNavController().navigate(R.id.action_onboardingFragment_to_loginFragment)
            }
        }
    }
}
