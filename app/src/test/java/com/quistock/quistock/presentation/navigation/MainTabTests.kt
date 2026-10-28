package com.quistock.quistock.presentation.navigation

import com.quistock.quistock.R
import com.quistock.quistock.domain.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainTabTests {
    @Test fun operatorHasFourTabsInApprovedOrder() {
        assertEquals(
            listOf(MainTab.HOME, MainTab.INTERFERENCES, MainTab.PRODUCTS, MainTab.CHATBOT),
            MainTab.forRole(UserRole.EMPLOYEE),
        )
    }

    @Test fun managerHasEmployeesBetweenProductsAndChatbot() {
        assertEquals(
            listOf(MainTab.HOME, MainTab.INTERFERENCES, MainTab.PRODUCTS, MainTab.EMPLOYEES, MainTab.CHATBOT),
            MainTab.forRole(UserRole.MANAGER),
        )
    }

    @Test fun unsupportedRoleGetsNoNavigation() {
        assertTrue(MainTab.forRole(UserRole.REGIONAL_MANAGER).isEmpty())
    }

    @Test fun interferencesDestinationUsesAuthenticatedRole() {
        assertEquals(R.id.interferencesFragment, MainTab.INTERFERENCES.destination(UserRole.EMPLOYEE))
        assertEquals(R.id.managerInterferencesFragment, MainTab.INTERFERENCES.destination(UserRole.MANAGER))
    }

    @Test fun secondaryScreensNeverShowBottomBar() {
        val hidden = listOf(
            R.id.profileFragment,
            R.id.editProfileFragment,
            R.id.notificationsFragment,
            R.id.productDetailFragment,
            R.id.managerOrderDetailFragment,
            R.id.managerPromotionDetailFragment,
            R.id.employeeDetailFragment,
            R.id.orderFragment,
            R.id.promotionFragment,
        )
        hidden.forEach { assertNull(MainTab.atRoot(it)) }
    }
}
