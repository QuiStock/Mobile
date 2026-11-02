package com.quistock.quistock.presentation.help

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.quistock.quistock.R

class ContactSentFragment : Fragment(R.layout.fragment_contact_sent) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.btnVoltarPerfilAjuda)?.setOnClickListener {
            findNavController().navigate(R.id.action_contactSentFragment_to_profileFragment)
        }
    }
}
