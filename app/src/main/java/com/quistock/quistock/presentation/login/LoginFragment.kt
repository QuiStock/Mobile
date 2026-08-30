package com.quistock.quistock.presentation.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentLoginBinding

class LoginFragment : Fragment() {
    private var bindingRef: FragmentLoginBinding? = null
    private val binding: FragmentLoginBinding
        get() = checkNotNull(bindingRef)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        bindingRef = FragmentLoginBinding.inflate(inflater, container, false)
        return bindingRef!!.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.btIrCadastro.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_cadastroPessoalFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bindingRef = null
    }
}
