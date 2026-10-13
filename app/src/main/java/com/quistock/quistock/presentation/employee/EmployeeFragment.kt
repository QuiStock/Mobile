package com.quistock.quistock.presentation.employee

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.quistock.quistock.R

class EmployeeFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_employee, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val employees = listOf(
            R.id.cardBrunaAlves to "Bruna Alves",
            R.id.cardRicardoMacedo to "Ricardo Macedo",
            R.id.cardBrenoAlmeida to "Breno Almeida",
        )

        employees.forEach { (cardId, name) ->
            view.findViewById<View>(cardId)
                .findViewById<TextView>(R.id.tvNomeFuncionario)
                .text = name
        }
    }
}
