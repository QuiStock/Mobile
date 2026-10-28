package com.quistock.quistock.presentation.activity

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.quistock.quistock.R
import com.quistock.quistock.databinding.ActivityMainBinding
import com.quistock.quistock.domain.model.ProfileRejection
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionFailure
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.presentation.common.ProfileBindingCallbacks
import com.quistock.quistock.presentation.navigation.BottomNavigation
import com.quistock.quistock.presentation.profile.LogoutProgressDialog
import com.quistock.quistock.presentation.profile.LogoutRemoteWarningDialog
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : AppCompatActivity() {
    private var _binding: ActivityMainBinding? = null
    val binding: ActivityMainBinding get() = _binding!!

    private val sessionViewModel: SessionViewModel by viewModel()
    private lateinit var bottomNavigation: BottomNavigation
    private val logoutBackBlocker = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = Unit
    }
    private val recoveryBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { /* Protected navigation remains hidden until recovery. */ }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportFragmentManager.registerFragmentLifecycleCallbacks(
            ProfileBindingCallbacks(sessionViewModel),
            true,
        )
        _binding = ActivityMainBinding.inflate(layoutInflater)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContentView(binding.root)
        bottomNavigation = BottomNavigation(this, binding, sessionViewModel)
        onBackPressedDispatcher.addCallback(this, logoutBackBlocker)
        onBackPressedDispatcher.addCallback(this, recoveryBack)
        binding.sessionRecoveryRetry.setOnClickListener { sessionViewModel.retrySession() }

        // Hide restored protected views before the first asynchronous state collection.
        binding.navHostFragment.visibility = View.INVISIBLE
        binding.sessionRecovery.visibility = View.VISIBLE
        binding.sessionRecoveryMessage.setText(R.string.profile_loading)
        binding.sessionRecoveryProgress.visibility = View.VISIBLE
        binding.sessionRecoveryRetry.visibility = View.GONE
        observeSession()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottom = maxOf(systemBars.bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottom)
            bottomNavigation.keyboardChanged(insets.isVisible(WindowInsetsCompat.Type.ime()))
            insets
        }
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        val state = sessionViewModel.session.state.value
        renderLogoutDialog(state == SessionState.LoggingOut || state is SessionState.LogoutFailed)
    }

    private fun observeSession() {
        val nav = (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    sessionViewModel.session.state,
                    sessionViewModel.recovery,
                    sessionViewModel.profile.state,
                ) { state, recovery, profile -> Triple(state, recovery, profile) }
                    .collect { (state, recovery, profile) ->
                        val gate = sessionGate(state, recovery, profile, nav.currentDestination?.id)
                        renderGate(state, recovery, gate)
                        navigateSession(nav, state, profile, gate.profilePending)
                    }
            }
        }
    }

    private fun sessionGate(
        state: SessionState,
        recovery: SessionRecoveryUiState,
        profile: ProfileState,
        destination: Int?,
    ): SessionGate {
        val profilePending = isProfilePending(state, profile)
        val profileLoading = state == SessionState.Restoring ||
            (profilePending && profile !is ProfileState.Failure && profile !is ProfileState.Rejected)
        val logoutBlocked = isLogoutBlocked(state)
        val blocked = state == SessionState.Restoring || isRecovering(state, recovery) ||
            logoutBlocked || profilePending
        val splashLoading = destination == R.id.splashFragment && profileLoading
        return SessionGate(profilePending, profileLoading, blocked, splashLoading, logoutBlocked)
    }

    private fun isLogoutBlocked(state: SessionState): Boolean =
        state == SessionState.LoggingOut || state is SessionState.LogoutFailed

    private fun isRecovering(state: SessionState, recovery: SessionRecoveryUiState): Boolean =
        state is SessionState.Failure ||
            (recovery.retrying && !isLogoutBlocked(state) && state !is SessionState.LoggedOut)

    private fun isProfilePending(state: SessionState, profile: ProfileState): Boolean = state == SessionState.Active &&
        (profile !is ProfileState.Ready || profile.generation != sessionViewModel.session.snapshot().generation)

    private fun renderGate(state: SessionState, recovery: SessionRecoveryUiState, gate: SessionGate) {
        val showRecovery = gate.blocked && !gate.splashLoading
        binding.navHostFragment.visibility = if (showRecovery) View.INVISIBLE else View.VISIBLE
        binding.navHostFragment.importantForAccessibility = if (gate.blocked) {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
        logoutBackBlocker.isEnabled = gate.logoutBlocked
        recoveryBack.isEnabled = gate.blocked && !gate.logoutBlocked
        binding.sessionRecovery.visibility = if (showRecovery && !gate.logoutBlocked) View.VISIBLE else View.GONE
        binding.sessionRecoveryProgress.visibility = if (recovery.retrying || gate.profileLoading) {
            View.VISIBLE
        } else {
            View.GONE
        }
        binding.sessionRecoveryRetry.visibility = if (gate.profileLoading) View.GONE else View.VISIBLE
        binding.sessionRecoveryRetry.isEnabled = !recovery.retrying && !recovery.retryBlocked
        binding.sessionRecoveryMessage.setText(gateMessage(state, gate))
        renderLogoutDialog(gate.logoutBlocked)
        bottomNavigation.update(gate.blocked)
    }

    private fun gateMessage(state: SessionState, gate: SessionGate): Int = when {
        gate.profileLoading -> R.string.profile_loading
        gate.profilePending -> R.string.profile_load_failed
        else -> sessionFailureMessage((state as? SessionState.Failure)?.reason)
    }

    private fun navigateSession(
        nav: NavController,
        state: SessionState,
        profile: ProfileState,
        profilePending: Boolean,
    ) {
        if (profilePending || state is SessionState.Failure) return
        if (sessionViewModel.consumeNavigation(state)) {
            sessionDestination(state, nav.currentDestination?.id)?.let { destination ->
                nav.navigate(
                    destination,
                    Bundle().apply {
                        putBoolean("expired", state == SessionState.Expired)
                        putBoolean("unexpected", state == SessionState.LocalFailure)
                        putBoolean(
                            LogoutRemoteWarningDialog.ARGUMENT,
                            state is SessionState.LoggedOut && !state.remoteConfirmed,
                        )
                        (profile as? ProfileState.Rejected)?.let {
                            putInt("profile_rejection_message", profileRejectionMessage(it))
                        }
                    },
                    navOptions {
                        popUpTo(R.id.nav_graph) { inclusive = true }
                        launchSingleTop = true
                    },
                )
            }
        }
    }

    private fun renderLogoutDialog(blocked: Boolean) {
        if (supportFragmentManager.isStateSaved) return
        val existing = supportFragmentManager.findFragmentByTag(LogoutProgressDialog.TAG) as? LogoutProgressDialog
        if (blocked && existing == null) {
            LogoutProgressDialog().showNow(supportFragmentManager, LogoutProgressDialog.TAG)
        } else if (!blocked) {
            existing?.dismissNow()
        }
    }
}

internal fun sessionFailureMessage(reason: SessionFailure?): Int = when (reason) {
    SessionFailure.NETWORK -> R.string.session_network
    SessionFailure.TIMEOUT -> R.string.session_timeout
    SessionFailure.SERVER -> R.string.session_server
    SessionFailure.RATE_LIMIT -> R.string.session_rate_limit
    else -> R.string.session_renewal_failed
}

internal fun sessionDestination(state: SessionState, current: Int?): Int? = when (state) {
    SessionState.LoggingOut, is SessionState.LogoutFailed -> null

    is SessionState.LoggedOut -> if (current == R.id.loginFragment) null else R.id.loginFragment

    SessionState.Restoring -> null

    SessionState.SignedOut -> if (current == R.id.loginFragment) null else R.id.loginFragment

    SessionState.Active -> if (current == R.id.loginFragment || current == R.id.splashFragment) {
        R.id.homeFragment
    } else {
        null
    }

    SessionState.Expired, SessionState.LocalFailure -> R.id.loginFragment

    is SessionState.Failure -> null
}

internal fun profileRejectionMessage(state: ProfileState.Rejected): Int = when (state.reason) {
    ProfileRejection.NO_STORE -> if (state.role == UserRole.MANAGER) {
        R.string.profile_no_store_manager
    } else {
        R.string.profile_no_store_employee
    }

    ProfileRejection.ROLE -> R.string.profile_mobile_denied

    else -> R.string.profile_access_denied
}

private data class SessionGate(
    val profilePending: Boolean,
    val profileLoading: Boolean,
    val blocked: Boolean,
    val splashLoading: Boolean,
    val logoutBlocked: Boolean,
)
