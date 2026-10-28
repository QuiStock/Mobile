package com.quistock.quistock.presentation.profile

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentEditProfileBinding
import com.quistock.quistock.presentation.activity.SessionViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class EditProfileFragment : Fragment(R.layout.fragment_edit_profile) {
    private val model: EditProfileViewModel by viewModel()
    private val sessionModel: SessionViewModel by activityViewModel()
    private val sources = ProfilePhotoSources(this) { model }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentEditProfileBinding.bind(view)
        binding.profileEmailInput.keyListener = null
        binding.profileEmailInput.isFocusable = false
        binding.profileEmailInput.isFocusableInTouchMode = false
        val renderer = EditProfileRenderer(binding, model.uploadAvailable) { sessionModel.photoBytes() }
        sources.cleanInterruptedCamera()
        binding.profileFullNameInput.doAfterTextChanged { model.rename(it.toString()) }
        binding.profileChangePhotoButton.setOnClickListener { sources.open() }
        binding.profileSaveChangesButton.setOnClickListener { model.save() }
        binding.profileCancelButton.setOnClickListener { leave() }
        binding.editProfileBackControl.setOnClickListener { leave() }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = leave()
            },
        )
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collectLatest { state ->
                    if (state.saved) completeSave() else renderUntilRetry(renderer, state)
                }
            }
        }
    }

    private suspend fun renderUntilRetry(renderer: EditProfileRenderer, state: EditProfileState) {
        renderer.render(state)
        val remaining = state.retryAt - profileEditNowMillis()
        if (remaining > 0) {
            delay(remaining)
            renderer.render(state)
        }
    }

    private fun leave() {
        val state = model.state.value
        if (state.busy) return
        if (state.dirty && state.original != null) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.profile_discard_title)
                .setNegativeButton(R.string.profile_continue_editing, null)
                .setPositiveButton(R.string.profile_discard) { _, _ -> close() }
                .show()
        } else {
            close()
        }
    }

    private fun close() {
        model.discard()
        if (findNavController().currentDestination?.id == R.id.editProfileFragment) {
            findNavController().popBackStack()
        }
    }

    private fun completeSave() {
        val nav = findNavController()
        if (nav.currentDestination?.id == R.id.editProfileFragment) {
            nav.previousBackStackEntry?.savedStateHandle?.set("profile_updated", true)
            close()
        }
    }
}
