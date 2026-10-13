package com.quistock.quistock.presentation.help

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.quistock.quistock.R

class FaqFragment : Fragment(R.layout.fragment_faq) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listOf(
            Triple(R.id.faq_card_1, R.id.faq_answer_1, R.id.faq_arrow_1),
            Triple(R.id.faq_card_2, R.id.faq_answer_2, R.id.faq_arrow_2),
            Triple(R.id.faq_card_3, R.id.faq_answer_3, R.id.faq_arrow_3),
            Triple(R.id.faq_card_4, R.id.faq_answer_4, R.id.faq_arrow_4),
        ).forEach { (cardId, answerId, arrowId) ->
            val card = view.findViewById<View>(cardId)
            val answer = view.findViewById<View>(answerId)
            val arrow = view.findViewById<ImageView>(arrowId)

            card.setOnClickListener {
                val isExpanded = answer.visibility != View.VISIBLE
                answer.visibility = if (isExpanded) View.VISIBLE else View.GONE
                arrow.setImageResource(if (isExpanded) R.drawable.arrow_up else R.drawable.arrow_down)
            }
        }

        view.findViewById<View>(R.id.faq_contact_link).setOnClickListener {
            findNavController().navigate(R.id.action_faqFragment_to_contactFragment)
        }

        view.findViewById<View>(R.id.faq_back_control).setOnClickListener {
            findNavController().navigate(R.id.action_faqFragment_to_profileFragment)
        }
    }
}
