package com.quistock.quistock.app.navigation.demo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.quistock.quistock.R
import com.quistock.quistock.databinding.ActivityDemoBinding

class DemoActivity : AppCompatActivity() {
    var manager: Boolean = false
        private set

    val nav get() = (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = savedInstanceState?.getBoolean("manager") ?: false
        val binding = ActivityDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, NavHostFragment.create(R.navigation.demo_graph))
                .setPrimaryNavigationFragment(null)
                .commitNow()
        }
        val host = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        supportFragmentManager.beginTransaction().setPrimaryNavigationFragment(host).commitNow()
        binding.demoRole.setText(if (manager) R.string.demo_manager else R.string.demo_operator)
        binding.demoRole.setOnClickListener {
            manager = !manager
            binding.demoRole.setText(if (manager) R.string.demo_manager else R.string.demo_operator)
            nav.navigate(
                R.id.homeFragment,
                null,
                navOptions {
                    popUpTo(nav.graph.id) { inclusive = false }
                    launchSingleTop = true
                },
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("manager", manager)
        super.onSaveInstanceState(outState)
    }
}
