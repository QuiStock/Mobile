package com.quistock.quistock.presentation.navigation

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.quistock.quistock.R

internal class TabContentCallbacks(
    private val memory: TabMemory,
    private val access: NavigationAccess,
    private val notifications: () -> Unit,
    private val resumed: () -> Unit,
) : FragmentManager.FragmentLifecycleCallbacks() {
    private val generations = java.util.WeakHashMap<Fragment, Long>()

    override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
        hideLegacyBars(v)
        bindNotifications(v)
        if (MainTab.atRoot(destination(f)) == null) return
        TabContent.disableDiskState(v)
        f.viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                val tab = MainTab.atRoot(destination(f))
                memory.content[tab]?.let { state ->
                    if (access.ready() != null) TabContent.restore(v, state, owner)
                }
            }
        })
    }

    override fun onFragmentPaused(fm: FragmentManager, f: Fragment) {
        val tab = MainTab.atRoot(destination(f))
        val view = f.view
        val current = access.ready()?.generation
        val sameSession = current != null && generations[f] == current
        if (tab != null && view != null && sameSession) {
            memory.content[tab] = TabContent.capture(view)
        }
    }

    override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
        access.ready()?.let { generations[f] = it.generation }
        resumed()
    }

    private fun destination(fragment: Fragment): Int? = when (fragment) {
        is com.quistock.quistock.presentation.home.HomeFragment -> R.id.homeFragment
        is com.quistock.quistock.presentation.interferences.InterferencesFragment -> R.id.interferencesFragment
        is com.quistock.quistock.presentation.interferences.ManagerInterferencesFragment ->
            R.id.managerInterferencesFragment
        is com.quistock.quistock.presentation.control.ProductControlFragment -> R.id.productControlFragment
        is com.quistock.quistock.presentation.employee.EmployeeFragment -> R.id.employeeFragment
        is com.quistock.quistock.presentation.chatbot.ChatbotFragment -> R.id.chatbotFragment
        else -> null
    }

    private fun hideLegacyBars(root: View) {
        root.findViewWithTag<View>("navigation_skeleton_bar")?.isVisible = false
        listOf(
            R.id.bottomNavigation,
            R.id.bottomNavigationInterferencias,
            R.id.bottomNavigationFuncionarios,
            R.id.bottomNavigationChatbot,
        ).forEach { id -> root.findViewById<View>(id)?.isVisible = false }
    }

    private fun bindNotifications(root: View) {
        TabContent.descendants(root).filter { it.contentDescription == "Notificações" }.forEach { view ->
            view.setOnClickListener { notifications() }
        }
    }
}
