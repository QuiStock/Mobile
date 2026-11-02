package com.quistock.quistock.presentation.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentProfileBinding
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.port.LocationPromptPreferences
import com.quistock.quistock.presentation.activity.SessionViewModel
import com.quistock.quistock.presentation.common.LOCATION_PERMISSIONS
import com.quistock.quistock.presentation.common.PopupFeedback
import com.quistock.quistock.presentation.common.openAppSettings
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class ProfileFragment : Fragment() {
    private var binding: FragmentProfileBinding? = null
    private val sessionViewModel: SessionViewModel by activityViewModel()
    private val locationViewModel: StoreLocationViewModel by viewModel()
    private val userLocationViewModel: UserLocationViewModel by viewModel()
    private val locationPrompt: LocationPromptPreferences by inject()
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        userLocationViewModel.refresh()
    }

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
        bindLocation(view)
        binding?.profileSignOutButton?.setOnClickListener {
            if (childFragmentManager.findFragmentByTag(LogoutConfirmationDialog.TAG) == null) {
                LogoutConfirmationDialog().showNow(childFragmentManager, LogoutConfirmationDialog.TAG)
            }
        }
    }

    private fun bindLocation(view: View) {
        val section = StoreLocationSection(
            binding = requireNotNull(binding).profileLocation,
            onOpen = { location ->
                if (!MapsLauncher.open(requireContext(), location)) {
                    PopupFeedback.showStatus(view, getString(R.string.location_no_maps_app), isError = true)
                }
            },
            onRetry = locationViewModel::retry,
            onUserLocationAction = ::onUserLocationAction,
        )
        section.attach(viewLifecycleOwner)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    sessionViewModel.profile.state.collect { state ->
                        (state as? ProfileState.Ready)?.profile?.storeId?.let(locationViewModel::load)
                    }
                }
                launch { locationViewModel.state.collect { section.render(it) } }
                // Also re-reads permission after returning from the system settings.
                userLocationViewModel.refresh()
                launch { userLocationViewModel.state.collect { section.renderUser(it) } }
            }
        }
    }

    private fun onUserLocationAction() {
        if (userLocationViewModel.state.value != UserLocationState.PermissionNeeded) {
            userLocationViewModel.refresh()
            return
        }
        val canAsk = LOCATION_PERMISSIONS.any { shouldShowRequestPermissionRationale(it) } || !locationPrompt.wasAsked()
        if (canAsk) {
            locationPrompt.markAsked()
            locationPermission.launch(LOCATION_PERMISSIONS)
        } else {
            // Denied permanently ("don't ask again"): only the system settings can grant it now.
            openAppSettings(requireContext())
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
