package com.quistock.quistock.presentation.navigation

import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.quistock.quistock.R
import com.quistock.quistock.databinding.ActivityMainBinding
import com.quistock.quistock.presentation.activity.MainActivity
import com.quistock.quistock.presentation.activity.SessionViewModel

/** Coordinates the real application only; demo navigation and shared layouts stay independent. */
internal class BottomNavigation(
    private val activity: MainActivity,
    private val binding: ActivityMainBinding,
    model: SessionViewModel,
) {
    private val memory = ViewModelProvider(activity)[TabMemory::class.java]
    private val access = NavigationAccess(model)
    private val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
    private val nav = host.navController
    private val bar = BottomBarView(binding.mainBottomNavigation, ::select)
    private var blocked = true
    private var keyboard = false
    private var transitioning = false
    private val back = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = navigateBack()
    }

    init {
        val callbacks = TabContentCallbacks(
            memory,
            access,
            ::openNotifications,
        ) { transitioning = false }
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(callbacks, true)
        host.childFragmentManager.primaryNavigationFragment?.let { fragment ->
            fragment.view?.let { callbacks.onFragmentViewCreated(host.childFragmentManager, fragment, it, null) }
        }
        activity.onBackPressedDispatcher.addCallback(activity, back)
        nav.addOnDestinationChangedListener { _, _, _ -> render() }
    }

    fun update(isBlocked: Boolean) {
        blocked = isBlocked
        if (access.shouldClear()) memory.clear()
        render()
    }

    fun keyboardChanged(visible: Boolean) {
        keyboard = visible
        render()
    }

    private fun render() {
        val ready = access.ready()
        val role = ready?.profile?.role
        if (ready != null) memory.bind(ready.generation, ready.profile.role)
        val tab = MainTab.atRoot(nav.currentDestination?.id)
        val available = !blocked && ready != null
        if (!available) ExitAppDialog.hide(activity.supportFragmentManager)
        back.isEnabled = available && tab != null
        bar.render(role, tab, available && tab != null && !keyboard)
        if (available && access.rejects(nav.currentDestination?.id)) {
            binding.navHostFragment.isVisible = false
            open(MainTab.HOME)
        } else if (available && tab != null) {
            memory.history.visit(tab)
        }
    }

    private fun select(tab: MainTab) {
        val role = access.ready()?.profile?.role
        if (!canNavigate() || role == null) return
        if (tab !in MainTab.forRole(role)) return
        if (MainTab.atRoot(nav.currentDestination?.id) == tab) {
            content()?.view?.let { TabContent.reselect(it, tab) }
        } else {
            open(tab)
        }
    }

    private fun open(tab: MainTab) {
        val ready = access.ready() ?: return
        if (activity.supportFragmentManager.isStateSaved) return
        transitioning = true
        nav.navigate(
            tab.destination(ready.profile.role),
            null,
            navOptions {
                popUpTo(R.id.nav_graph) { inclusive = false }
                launchSingleTop = true
            },
        )
        binding.navHostFragment.isVisible = true
    }

    private fun navigateBack() {
        if (!canNavigate()) return
        if (nav.currentDestination?.id == R.id.homeFragment) {
            val manager = activity.supportFragmentManager
            if (manager.findFragmentByTag(ExitAppDialog.TAG) == null) {
                ExitAppDialog().showNow(manager, ExitAppDialog.TAG)
            }
        } else {
            memory.history.previous()?.let(::open)
        }
    }

    private fun openNotifications() {
        if (canNavigate() && nav.currentDestination?.id != R.id.notificationsFragment) {
            transitioning = true
            nav.navigate(R.id.notificationsFragment, null, navOptions { launchSingleTop = true })
        }
    }

    private fun canNavigate(): Boolean {
        val ready = !blocked && !transitioning && access.ready() != null
        val resumed = content()?.lifecycle?.currentState == Lifecycle.State.RESUMED
        return ready && !activity.supportFragmentManager.isStateSaved && resumed
    }

    private fun content(): Fragment? = host.childFragmentManager.primaryNavigationFragment
}
