package com.quistock.quistock.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabHistoryTests {
    @Test fun backConsumesVisitsWithoutRecordingAnotherVisit() {
        val history = TabHistory()
        history.visit(MainTab.HOME)
        history.visit(MainTab.PRODUCTS)
        history.visit(MainTab.CHATBOT)

        assertEquals(MainTab.PRODUCTS, history.previous())
        assertEquals(MainTab.HOME, history.previous())
        assertNull(history.previous())
    }

    @Test fun reselectionDoesNotCreateHistory() {
        val history = TabHistory()
        history.visit(MainTab.HOME)
        history.visit(MainTab.PRODUCTS)
        history.visit(MainTab.PRODUCTS)

        assertEquals(MainTab.HOME, history.previous())
        assertNull(history.previous())
    }

    @Test fun intentionalRevisitIsPartOfHistory() {
        val history = TabHistory()
        history.visit(MainTab.HOME)
        history.visit(MainTab.PRODUCTS)
        history.visit(MainTab.CHATBOT)
        history.visit(MainTab.PRODUCTS)

        assertEquals(MainTab.CHATBOT, history.previous())
        assertEquals(MainTab.PRODUCTS, history.previous())
    }

    @Test fun sessionResetDiscardsPreviousVisits() {
        val history = TabHistory()
        history.visit(MainTab.HOME)
        history.visit(MainTab.EMPLOYEES)
        history.clear()
        history.visit(MainTab.HOME)

        assertNull(history.previous())
    }
}
