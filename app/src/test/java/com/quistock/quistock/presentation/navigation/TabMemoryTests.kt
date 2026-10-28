package com.quistock.quistock.presentation.navigation

import com.quistock.quistock.domain.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabMemoryTests {
    @Test fun sameSessionRetainsFiltersAndHistory() {
        val memory = TabMemory()
        memory.bind(1, UserRole.MANAGER)
        val state = TabContentState(mapOf(1 to 40), emptyMap(), mapOf(2 to "query"))
        memory.content[MainTab.PRODUCTS] = state
        memory.history.visit(MainTab.PRODUCTS)
        memory.bind(1, UserRole.MANAGER)

        assertEquals(state, memory.content[MainTab.PRODUCTS])
        assertEquals(MainTab.HOME, memory.history.previous())
    }

    @Test fun newGenerationRemovesPreviousContentAndManagerHistory() {
        val memory = TabMemory()
        memory.bind(1, UserRole.MANAGER)
        memory.history.visit(MainTab.EMPLOYEES)
        memory.content[MainTab.EMPLOYEES] = TabContentState(emptyMap(), emptyMap(), emptyMap())
        memory.bind(2, UserRole.EMPLOYEE)

        assertTrue(memory.content.isEmpty())
        assertNull(memory.history.previous())
    }

    @Test fun roleChangeAndExplicitClearDiscardContent() {
        val memory = TabMemory()
        memory.bind(1, UserRole.MANAGER)
        memory.history.visit(MainTab.EMPLOYEES)
        memory.bind(1, UserRole.EMPLOYEE)
        assertNull(memory.history.previous())
        memory.history.visit(MainTab.CHATBOT)
        memory.clear()
        assertNull(memory.history.previous())
    }

    @Test fun freshProcessHasNoFiltersOrVisitsFromPreviousInstance() {
        val old = TabMemory()
        old.bind(1, UserRole.EMPLOYEE)
        old.history.visit(MainTab.CHATBOT)
        val fresh = TabMemory()
        fresh.bind(1, UserRole.EMPLOYEE)

        assertTrue(fresh.content.isEmpty())
        assertNull(fresh.history.previous())
    }
}
