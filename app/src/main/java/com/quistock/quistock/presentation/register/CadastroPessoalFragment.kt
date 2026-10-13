package com.quistock.quistock.presentation.register

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.quistock.quistock.R
import com.quistock.quistock.presentation.common.PopupFeedback

class CadastroPessoalFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_cadastro_pessoal, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val name = view.findViewById<EditText>(R.id.nome_cadastro)
        val email = view.findViewById<EditText>(R.id.email_cadastro)
        val password = view.findViewById<EditText>(R.id.senha_cadastro)
        val confirmPassword = view.findViewById<EditText>(R.id.Csenha_cadastro)
        view.findViewById<View>(R.id.bt_prox_cadastro).setOnClickListener {
            val message = when {
                listOf(name, email, password, confirmPassword).any { it.text.isNullOrBlank() } ->
                    R.string.popup_register_required

                !Patterns.EMAIL_ADDRESS.matcher(email.text.toString().trim()).matches() -> R.string.popup_register_email

                password.text.toString() != confirmPassword.text.toString() -> R.string.popup_register_password_mismatch

                else -> null
            }
            if (message != null) {
                PopupFeedback.showStatus(view, getString(message), isError = true)
            }
        }
    }
}
