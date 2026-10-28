package com.quistock.quistock.presentation.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentProfileBinding
import com.quistock.quistock.presentation.common.PopupFeedback

class ProfileFragment : Fragment() {
    private var binding: FragmentProfileBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentProfileBinding.inflate(inflater, container, false).also {
            binding = it
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<Boolean>("profile_updated")?.observe(viewLifecycleOwner) { updated ->
                if (updated) {
                    PopupFeedback.showStatus(view, getString(R.string.profile_updated), isError = false)
                    findNavController().currentBackStackEntry?.savedStateHandle?.remove<Boolean>("profile_updated")
                }
            }

        binding?.profileEditButton?.setOnClickListener {
            findNavController().navigate(R.id.editProfileFragment)
        }
        binding?.profileBackControl?.setOnClickListener {
            findNavController().popBackStack(R.id.homeFragment, false)
        }
        binding?.profileHelpRow?.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_faqFragment)
        }
        binding?.profileSignOutButton?.setOnClickListener {
            if (childFragmentManager.findFragmentByTag(LogoutConfirmationDialog.TAG) == null) {
                LogoutConfirmationDialog().showNow(childFragmentManager, LogoutConfirmationDialog.TAG)
            }
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
