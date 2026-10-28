package com.quistock.quistock.presentation.navigation

import com.quistock.quistock.R
import com.quistock.quistock.domain.model.UserRole

internal enum class MainTab(val label: Int, val icon: Int, val activeIcon: Int) {
    HOME(R.string.navigation_home, R.drawable.ic_home, R.drawable.ic_home_active),
    INTERFERENCES(
        R.string.navigation_interferences,
        R.drawable.ic_interferences,
        R.drawable.ic_interferences_active,
    ),
    PRODUCTS(R.string.navigation_products, R.drawable.ic_product_control, R.drawable.ic_product_control_active),
    EMPLOYEES(R.string.navigation_employees, R.drawable.ic_employee, R.drawable.ic_employee_active),
    CHATBOT(R.string.navigation_chatbot, R.drawable.ic_chatbot, R.drawable.ic_chatbot_active),
    ;

    fun destination(role: UserRole): Int = when (this) {
        HOME -> R.id.homeFragment
        INTERFERENCES -> if (role == UserRole.MANAGER) {
            R.id.managerInterferencesFragment
        } else {
            R.id.interferencesFragment
        }
        PRODUCTS -> R.id.productControlFragment
        EMPLOYEES -> R.id.employeeFragment
        CHATBOT -> R.id.chatbotFragment
    }

    companion object {
        fun forRole(role: UserRole): List<MainTab> = when (role) {
            UserRole.EMPLOYEE -> listOf(HOME, INTERFERENCES, PRODUCTS, CHATBOT)
            UserRole.MANAGER -> entries.toList()
            else -> emptyList()
        }

        fun atRoot(destination: Int?): MainTab? = when (destination) {
            R.id.homeFragment -> HOME
            R.id.interferencesFragment, R.id.managerInterferencesFragment -> INTERFERENCES
            R.id.productControlFragment -> PRODUCTS
            R.id.employeeFragment -> EMPLOYEES
            R.id.chatbotFragment -> CHATBOT
            else -> null
        }
    }
}
