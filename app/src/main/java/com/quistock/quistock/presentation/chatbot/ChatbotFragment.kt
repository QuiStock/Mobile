package com.quistock.quistock.presentation.chatbot

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.quistock.quistock.R
import com.quistock.quistock.domain.model.ChatbotConversation
import com.quistock.quistock.presentation.common.PopupFeedback
import com.quistock.quistock.presentation.common.SkeletonLoadingScreen
import com.quistock.quistock.presentation.common.SkeletonScreenType
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class ChatbotFragment : Fragment() {
    private val viewModel: ChatbotViewModel by activityViewModel()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SkeletonLoadingScreen.wrap(
            inflater.inflate(R.layout.fragment_chatbot, container, false),
            SkeletonScreenType.CHATBOT,
        )

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        viewModel.synchronizeSession()
        viewModel.load()
        val menuOverlay = view.findViewById<View>(R.id.chatMenuOverlay)
        val input = view.findViewById<EditText>(R.id.etPerguntaChatbot).apply { isSaveEnabled = false }
        bindMenu(view, menuOverlay)
        bindInput(view, input)
        observeMessages(viewLifecycleOwner, view.findViewById(R.id.containerMensagens))
        observeEndedConversations(viewLifecycleOwner, view.findViewById(R.id.recentConversations), menuOverlay)
        observeUiState(view, input, view.findViewById(R.id.btnEnviarPergunta))
    }

    private fun bindMenu(view: View, overlay: View) {
        view.findViewById<View>(R.id.btnMenuChatbot).setOnClickListener { overlay.visibility = View.VISIBLE }
        overlay.setOnClickListener { overlay.visibility = View.GONE }
        view.findViewById<View>(R.id.chatMenuPanel).setOnClickListener { }
        view.findViewById<View>(R.id.btnNewChat).setOnClickListener {
            viewModel.newConversation()
            overlay.visibility = View.GONE
        }
    }

    private fun bindInput(view: View, input: EditText) {
        view.findViewById<View>(R.id.btnEnviarPergunta).setOnClickListener {
            val message = input.text.toString().trim()
            if (message.isEmpty()) {
                PopupFeedback.showStatus(view, getString(R.string.chatbot_empty_message), isError = true)
            } else {
                input.text.clear()
                viewModel.sendMessage(message)
            }
        }
    }

    private fun observeMessages(owner: LifecycleOwner, container: LinearLayout) {
        viewModel.messages.observe(owner) { conversation ->
            container.removeAllViews()
            conversation.forEach { addMessage(container, it.text, it.isUser) }
        }
    }

    private fun observeEndedConversations(owner: LifecycleOwner, container: LinearLayout, overlay: View) {
        viewModel.endedConversations.observe(owner) { conversations ->
            container.removeAllViews()
            if (conversations.isEmpty()) {
                container.addView(
                    TextView(requireContext()).apply {
                        setText(R.string.chatbot_no_history)
                        textSize = HISTORY_TEXT_SIZE_SP
                        setTextColor(HISTORY_TEXT_COLOR)
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        minHeight = resources.getDimensionPixelSize(R.dimen.chatbot_history_row_min_height)
                    },
                )
            }
            conversations.forEach { conversation ->
                container.addView(
                    historyRow(conversation) {
                        viewModel.openHistory(conversation.id)
                        overlay.visibility = View.GONE
                    },
                )
            }
        }
    }

    private fun historyRow(conversation: ChatbotConversation, onClick: () -> Unit) = TextView(requireContext()).apply {
        text = conversation.title.ifBlank { conversation.updatedAt }
        textSize = HISTORY_TEXT_SIZE_SP
        setTextColor(HISTORY_TEXT_COLOR)
        gravity = android.view.Gravity.CENTER_VERTICAL
        minHeight = resources.getDimensionPixelSize(R.dimen.chatbot_history_row_min_height)
        setPadding(resources.getDimensionPixelSize(R.dimen.chatbot_history_row_padding), 0, 0, 0)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun observeUiState(view: View, input: EditText, sendButton: View) {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            val busy = state == ChatbotUiState.Loading || state == ChatbotUiState.LoadingConversation ||
                state == ChatbotUiState.Ending
            input.isEnabled = !busy
            sendButton.isEnabled = !busy
            when (state) {
                ChatbotUiState.Answered -> Unit

                ChatbotUiState.Rejected -> PopupFeedback.showStatus(
                    view,
                    getString(R.string.chatbot_request_rejected),
                    isError = true,
                )

                ChatbotUiState.Error.SEND_UNCERTAIN -> AlertDialog.Builder(requireContext())
                    .setMessage(R.string.chatbot_retry_may_duplicate)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.chatbot_retry) { _, _ -> viewModel.retryLastMessage() }
                    .show()

                ChatbotUiState.Error.NOT_LOGGED_IN -> showError(view, R.string.chatbot_login_required)

                ChatbotUiState.Error.REQUEST_FAILED -> showError(view, R.string.chatbot_request_failed)

                ChatbotUiState.Error.HISTORY_FAILED -> showError(view, R.string.chatbot_history_failed)

                ChatbotUiState.Error.HISTORY_REFRESH_FAILED -> showError(view, R.string.chatbot_history_refresh_failed)

                ChatbotUiState.Error.END_UNCERTAIN -> showError(view, R.string.chatbot_end_uncertain)

                else -> Unit
            }
        }
    }

    private fun showError(view: View, message: Int) = PopupFeedback.showStatus(view, getString(message), isError = true)

    private fun addMessage(container: LinearLayout, message: String, isUser: Boolean) {
        val text = TextView(requireContext()).apply {
            this.text = message
            textSize = MESSAGE_TEXT_SIZE_SP
            setTextColor(android.graphics.Color.WHITE)
            setPadding(MESSAGE_PADDING_PX, MESSAGE_VERTICAL_PADDING_PX, MESSAGE_PADDING_PX, MESSAGE_VERTICAL_PADDING_PX)
            setBackgroundResource(if (isUser) R.drawable.bg_chat_selected else R.drawable.bg_popup_dialog)
            val bubbleWidth = if (isUser) USER_BUBBLE_WIDTH else ASSISTANT_BUBBLE_WIDTH
            val params = LinearLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * bubbleWidth).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            params.gravity = if (isUser) {
                android.view.Gravity.END
            } else {
                android.view.Gravity.START
            }
            params.bottomMargin = MESSAGE_BOTTOM_MARGIN_PX
            layoutParams = params
        }
        container.addView(text)
    }

    private companion object {
        const val HISTORY_TEXT_SIZE_SP = 13f
        const val HISTORY_TEXT_COLOR = 0xFFD2C7D8.toInt()
        const val MESSAGE_TEXT_SIZE_SP = 14f
        const val MESSAGE_PADDING_PX = 16
        const val MESSAGE_VERTICAL_PADDING_PX = 12
        const val MESSAGE_BOTTOM_MARGIN_PX = 12
        const val USER_BUBBLE_WIDTH = 0.72f
        const val ASSISTANT_BUBBLE_WIDTH = 0.78f
    }
}
