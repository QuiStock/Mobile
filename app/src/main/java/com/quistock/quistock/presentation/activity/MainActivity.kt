package com.quistock.quistock.presentation.activity

import android.os.Bundle
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
import com.quistock.quistock.domain.model.SessionState
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : AppCompatActivity() {
    private var _binding: ActivityMainBinding? = null
    val binding: ActivityMainBinding get() = _binding!!

    private val sessionViewModel: SessionViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _binding = ActivityMainBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

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
                sessionViewModel.session.state.collect { state ->
                    binding.navHostFragment.visibility = if (state == SessionState.Restoring) {
                        android.view.View.INVISIBLE
                    } else {
                        android.view.View.VISIBLE
                    }
                    if (!sessionViewModel.consumeNavigation(state)) return@collect
                    val destination = destination(state, nav.currentDestination?.id)
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

    private fun destination(state: SessionState, current: Int?): Int? = when (state) {
        SessionState.Restoring -> null

        SessionState.SignedOut -> if (current == R.id.loginFragment) null else R.id.loginFragment

        SessionState.Active -> if (current == R.id.loginFragment) R.id.homeFragment else null

        SessionState.Expired, SessionState.LocalFailure -> R.id.loginFragment

        is SessionState.Failure -> when {
            current == R.id.loginFragment -> R.id.homeFragment
            else -> null
        }
    }
}
