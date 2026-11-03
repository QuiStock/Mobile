package com.quistock.quistock.presentation.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentProfileBinding
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.RoleAlertType
import com.quistock.quistock.domain.port.LocationPromptPreferences
import com.quistock.quistock.presentation.activity.SessionViewModel
import com.quistock.quistock.presentation.common.LOCATION_PERMISSIONS
import com.quistock.quistock.presentation.common.PopupFeedback
import com.quistock.quistock.presentation.common.openAppSettings
import com.quistock.quistock.presentation.notifications.needsNotificationPermission
import com.quistock.quistock.presentation.notifications.notificationPermissionLauncher
import com.quistock.quistock.presentation.notifications.openNotificationSettings
import com.quistock.quistock.presentation.notifications.requestNotifications
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
    private val alertPreferences: AlertPreferencesViewModel by viewModel()
    private val notificationPermission = notificationPermissionLauncher { granted ->
        renderAlertPermission()
        if (granted) sessionViewModel.alerts.refresh()
    }
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
        bindAlerts()
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

    private fun bindAlerts() {
        val views = requireNotNull(binding).profileAlerts
        val switches = mapOf(
            RoleAlertType.NEAR_EXPIRY to views.roleAlertsExpiry,
            RoleAlertType.PENDING_APPROVAL to views.roleAlertsPending,
            RoleAlertType.REQUEST_APPROVED to views.roleAlertsApproved,
        )
        switches.forEach { (type, switch) ->
            switch.setOnCheckedChangeListener { _, checked ->
                alertPreferences.set(type, checked)
            }
        }
        views.roleAlertsPermission.setOnClickListener { askNotifications() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                renderAlertPermission()
                launch {
                    sessionViewModel.profile.state.collect { if (it is ProfileState.Ready) alertPreferences.load() }
                }
                alertPreferences.state.collect { enabled ->
                    views.root.isVisible = enabled.isNotEmpty()
                    switches.forEach { (type, switch) ->
                        switch.isVisible = type in enabled
                        switch.isChecked = enabled[type] == true
                    }
                }
            }
        }
    }

    private fun renderAlertPermission() {
        binding?.profileAlerts?.roleAlertsPermission?.isVisible = needsNotificationPermission(requireContext())
    }

    private fun askNotifications() {
        val permission = android.Manifest.permission.POST_NOTIFICATIONS
        if (shouldShowRequestPermissionRationale(permission) || !notificationAskedHere) {
            notificationAskedHere = true
            notificationPermission.requestNotifications()
        } else {
            // Denied permanently: only the system settings can enable notifications now.
            openNotificationSettings(requireContext())
        }
    }

    private var notificationAskedHere = false

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
