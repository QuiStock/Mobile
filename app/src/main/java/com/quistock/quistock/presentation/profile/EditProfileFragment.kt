package com.quistock.quistock.presentation.profile

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentEditProfileBinding
import com.quistock.quistock.presentation.common.PopupFeedback

class EditProfileFragment : Fragment(R.layout.fragment_edit_profile) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentEditProfileBinding.bind(view)
        binding.profileCancelButton.setOnClickListener {
            // In future versions, cancel button should actually cancel the operation
            // instead of only returning to the previous page
            if (findNavController().currentDestination?.id == R.id.editProfileFragment) {
                findNavController().popBackStack()
            }
        }
        binding.editProfileBackControl.setOnClickListener {
            if (findNavController().currentDestination?.id == R.id.editProfileFragment) {
                findNavController().popBackStack()
            }
        }
        binding.profileSaveChangesButton.setOnClickListener {
            PopupFeedback.showStatus(view, getString(R.string.profile_save_not_implemented), isError = true)
        }
    }
}
