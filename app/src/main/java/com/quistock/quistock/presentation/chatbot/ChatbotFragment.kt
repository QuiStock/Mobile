package com.quistock.quistock.presentation.chatbot

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.synchronizeSession()
        viewModel.load()
        val menuOverlay = view.findViewById<View>(R.id.chatMenuOverlay)
        val input = view.findViewById<EditText>(R.id.etPerguntaChatbot).apply { isSaveEnabled = false }
        val welcomeLayout = view.findViewById<View>(R.id.layoutWelcomeChatbot)
        val containerMensagens = view.findViewById<LinearLayout>(R.id.containerMensagens)
        val typing = TypingIndicatorView(requireContext())

        bindMenu(view, menuOverlay)
        bindInput(view, input)
        observeMessages(viewLifecycleOwner, containerMensagens, welcomeLayout, typing)
        observeEndedConversations(viewLifecycleOwner, view.findViewById(R.id.recentConversations), menuOverlay)
        observeUiState(view, input, view.findViewById(R.id.btnEnviarPergunta), typing, containerMensagens)
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
                if (viewModel.sendMessage(message)) {
                    input.text.clear()
                    view.findViewById<ScrollView>(R.id.scrollMensagens)?.post {
                        view.findViewById<ScrollView>(R.id.scrollMensagens)?.fullScroll(View.FOCUS_DOWN)
                    }
                }
            }
        }
    }

    private fun observeMessages(
        owner: LifecycleOwner,
        container: LinearLayout,
        welcomeLayout: View?,
        typing: TypingIndicatorView,
    ) {
        viewModel.messages.observe(owner) { conversation ->
            container.removeAllViews()
            if (conversation.isNullOrEmpty()) {
                welcomeLayout?.visibility = View.VISIBLE
            } else {
                welcomeLayout?.visibility = View.GONE
                conversation.forEach { addMessage(container, it.text, it.isUser) }
            }
            typing.update(container, container.parent as? ScrollView, viewModel.uiState.value == ChatbotUiState.Loading)
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
                        setTextColor(context.getColor(R.color.color_chat_history))
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
        setTextColor(context.getColor(R.color.color_chat_history))
        gravity = android.view.Gravity.CENTER_VERTICAL
        minHeight = resources.getDimensionPixelSize(R.dimen.chatbot_history_row_min_height)
        setPadding(resources.getDimensionPixelSize(R.dimen.chatbot_history_row_padding), 0, 0, 0)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun observeUiState(
        view: View,
        input: EditText,
        sendButton: View,
        typing: TypingIndicatorView,
        messages: LinearLayout,
    ) {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            val busy = state == ChatbotUiState.Loading || state == ChatbotUiState.LoadingConversation ||
                state == ChatbotUiState.Ending
            input.isEnabled = !busy
            sendButton.isEnabled = !busy
            typing.update(messages, messages.parent as? ScrollView, state == ChatbotUiState.Loading)
            showUiFeedback(view, state)
        }
    }

    private fun showUiFeedback(view: View, state: ChatbotUiState) {
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

            ChatbotUiState.Error.PROFILE_LOADING -> showError(view, R.string.profile_loading)

            ChatbotUiState.Error.PROFILE_FAILED -> showError(view, R.string.profile_load_failed)

            ChatbotUiState.Error.PROFILE_NO_STORE_EMPLOYEE -> showError(view, R.string.profile_no_store_employee)

            ChatbotUiState.Error.PROFILE_NO_STORE_MANAGER -> showError(view, R.string.profile_no_store_manager)

            ChatbotUiState.Error.REQUEST_FAILED -> showError(view, R.string.chatbot_request_failed)

            ChatbotUiState.Error.HISTORY_FAILED -> showError(view, R.string.chatbot_history_failed)

            ChatbotUiState.Error.HISTORY_REFRESH_FAILED -> showError(view, R.string.chatbot_history_refresh_failed)

            ChatbotUiState.Error.END_UNCERTAIN -> showError(view, R.string.chatbot_end_uncertain)

            else -> Unit
        }
    }

    private fun showError(view: View, message: Int) = PopupFeedback.showStatus(view, getString(message), isError = true)

    private fun addMessage(container: LinearLayout, message: String, isUser: Boolean) {
        val density = resources.displayMetrics.density
        val text = TextView(requireContext()).apply {
            this.text = message
            textSize = 14f
            setTextColor(
                if (isUser) {
                    android.graphics.Color.WHITE
                } else {
                    ContextCompat.getColor(context, R.color.color_text_title)
                },
            )
            val padHorizontal = (16 * density).toInt()
            val padVertical = (10 * density).toInt()
            setPadding(padHorizontal, padVertical, padHorizontal, padVertical)
            setBackgroundResource(if (isUser) R.drawable.bg_chat_bubble_user else R.drawable.bg_chat_bubble_bot)
            maxWidth = (resources.displayMetrics.widthPixels * 0.78f).toInt()
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = if (isUser) android.view.Gravity.END else android.view.Gravity.START
                bottomMargin = (8 * density).toInt()
                if (isUser) {
                    marginStart = (48 * density).toInt()
                } else {
                    marginEnd = (48 * density).toInt()
                }
            }
            layoutParams = params
        }
        container.addView(text)
    }

    private companion object {
        const val HISTORY_TEXT_SIZE_SP = 13f
    }
}
