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
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.quistock.quistock.R
import com.quistock.quistock.databinding.ActivityMainBinding
import com.quistock.quistock.domain.model.SessionFailure
import com.quistock.quistock.domain.model.SessionState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : AppCompatActivity() {
    private var _binding: ActivityMainBinding? = null
    val binding: ActivityMainBinding get() = _binding!!

    private val sessionViewModel: SessionViewModel by viewModel()
    private val recoveryBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { /* Protected navigation remains hidden until recovery. */ }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _binding = ActivityMainBinding.inflate(layoutInflater)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContentView(binding.root)
        onBackPressedDispatcher.addCallback(this, recoveryBack)
        binding.sessionRecoveryRetry.setOnClickListener { sessionViewModel.retrySession() }

        observeSession()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
    private fun observeSession() {
        val nav = (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(sessionViewModel.session.state, sessionViewModel.recovery) { state, recovery ->
                    state to recovery
                }.collect { (state, recovery) ->
                    val blocked = state is SessionState.Failure || recovery.retrying
                    binding.navHostFragment.visibility = if (state == SessionState.Restoring || blocked) {
                        View.INVISIBLE
                    } else {
                        View.VISIBLE
                    }
                    binding.navHostFragment.importantForAccessibility = if (blocked) {
                        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    } else {
                        View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                    }
                    recoveryBack.isEnabled = blocked
                    binding.sessionRecovery.visibility = if (blocked) View.VISIBLE else View.GONE
                    binding.sessionRecoveryProgress.visibility = if (recovery.retrying) View.VISIBLE else View.GONE
                    binding.sessionRecoveryRetry.isEnabled = !recovery.retrying && !recovery.retryBlocked
                    binding.sessionRecoveryMessage.setText(
                        sessionFailureMessage((state as? SessionState.Failure)?.reason),
                    )
                    if (!sessionViewModel.consumeNavigation(state)) return@collect
                    val destination = sessionDestination(state, nav.currentDestination?.id)
                    if (destination != null) {
                        nav.navigate(
                            destination,
                            Bundle().apply {
                                putBoolean("expired", state == SessionState.Expired)
                                putBoolean("unexpected", state == SessionState.LocalFailure)
                            },
                            navOptions {
                                popUpTo(R.id.nav_graph) { inclusive = true }
                                launchSingleTop = true
                            },
                        )
                    }
                }
            }
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

    is SessionState.Failure -> when {
        current == R.id.loginFragment || current == R.id.splashFragment -> R.id.homeFragment
        else -> null
    }
}
