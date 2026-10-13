package com.quistock.quistock.presentation.help

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.quistock.quistock.R

class ContactFragment : Fragment(R.layout.fragment_contact) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.contact_back_control).setOnClickListener {
            findNavController().navigate(R.id.action_contactFragment_to_faqFragment)
        }
    }
}
