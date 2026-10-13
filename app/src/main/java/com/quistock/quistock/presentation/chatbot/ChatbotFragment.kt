package com.quistock.quistock.presentation.chatbot

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.quistock.quistock.R
import com.quistock.quistock.presentation.common.PopupFeedback
import org.koin.androidx.viewmodel.ext.android.viewModel

class ChatbotFragment : Fragment() {
    private val viewModel: ChatbotViewModel by viewModel()
    private var lastMessage: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_chatbot, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val menuOverlay = view.findViewById<View>(R.id.chatMenuOverlay)
        val menuPanel = view.findViewById<View>(R.id.chatMenuPanel)
        val messageInput = view.findViewById<EditText>(R.id.etPerguntaChatbot)
        val messages = view.findViewById<LinearLayout>(R.id.containerMensagens)

        view.findViewById<View>(R.id.btnMenuChatbot).setOnClickListener {
            menuOverlay.visibility = View.VISIBLE
        }
        menuOverlay.setOnClickListener { menuOverlay.visibility = View.GONE }
        menuPanel.setOnClickListener { }
        view.findViewById<View>(R.id.btnNewChat).setOnClickListener {
            messages.removeAllViews()
            messageInput.text.clear()
            lastMessage = null
            viewModel.clearConversation()
            menuOverlay.visibility = View.GONE
            view.findViewById<TextView>(R.id.tvTituloChatbot).text = getString(R.string.chatbot_welcome_title)
        }
        view.findViewById<View>(R.id.btnEnviarPergunta).setOnClickListener {
            val message = messageInput.text.toString().trim()
            if (message.isEmpty()) {
                PopupFeedback.showStatus(view, getString(R.string.chatbot_empty_message), isError = true)
                return@setOnClickListener
            }
            lastMessage = message
            addMessage(messages, message, isUser = true)
            messageInput.text.clear()
            viewModel.sendMessage(message)
        }

        viewModel.answer.observe(viewLifecycleOwner) { response ->
            response?.let { addMessage(messages, it.answer, isUser = false) }
        }
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                ChatbotUiState.Answered -> Unit

                is ChatbotUiState.Error -> {
                    when (state) {
                        ChatbotUiState.Error.NETWORK -> {
                            PopupFeedback.showNoConnection(requireContext()) {
                                lastMessage?.let(viewModel::sendMessage)
                            }
                        }

                        ChatbotUiState.Error.NOT_LOGGED_IN -> {
                            PopupFeedback.showStatus(view, getString(R.string.chatbot_login_required), isError = true)
                        }

                        ChatbotUiState.Error.REQUEST_FAILED -> {
                            PopupFeedback.showStatus(view, getString(R.string.chatbot_request_failed), isError = true)
                        }
                    }
                }

                else -> Unit
            }
        }
    }

    private fun addMessage(container: LinearLayout, message: String, isUser: Boolean) {
        val text = TextView(requireContext()).apply {
            text = message
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(16, 12, 16, 12)
            setBackgroundResource(if (isUser) R.drawable.bg_chat_selected else R.drawable.bg_popup_dialog)
            val params = LinearLayout.LayoutParams(
                if (isUser) {
                    (resources.displayMetrics.widthPixels * 0.72f).toInt()
                } else {
                    (resources.displayMetrics.widthPixels * 0.78f).toInt()
                },
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            params.gravity = if (isUser) android.view.Gravity.END else android.view.Gravity.START
            params.bottomMargin = 12
            layoutParams = params
        }
        container.addView(text)
    }
}
