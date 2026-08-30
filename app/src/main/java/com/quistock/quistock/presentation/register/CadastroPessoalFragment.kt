package com.quistock.quistock.presentation.register

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentCadastroPessoalBinding

class CadastroPessoalFragment : Fragment() {
    private var bindingRef: FragmentCadastroPessoalBinding? = null
    private val binding: FragmentCadastroPessoalBinding
        get() = checkNotNull(bindingRef)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        bindingRef = FragmentCadastroPessoalBinding.inflate(inflater, container, false)
        return bindingRef!!.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.btIrLogin.setOnClickListener {
            findNavController().navigate(R.id.action_cadastroPessoalFragment_to_loginFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bindingRef = null
    }
}
