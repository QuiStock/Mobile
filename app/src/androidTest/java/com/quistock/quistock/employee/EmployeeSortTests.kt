package com.quistock.quistock.employee

import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quistock.quistock.AuthenticatedSessionRule
import com.quistock.quistock.R
import com.quistock.quistock.domain.model.TeamMember
import com.quistock.quistock.domain.model.TeamResult
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.port.TeamRepository
import com.quistock.quistock.navigation.awaitNavigationReady
import com.quistock.quistock.presentation.activity.MainActivity
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class EmployeeSortTests {
    @get:Rule val session = AuthenticatedSessionRule(UserRole.MANAGER)

    private lateinit var previous: TeamRepository
    private val coreOrder = listOf("Ana", "Bruna", "Ágata")

    @Before
    fun installTeam() {
        val koin = GlobalContext.get()
        previous = koin.get()
        val team: TeamRepository = mockk()
        coEvery { team.list(any()) } returns TeamResult.Success(
            listOf(
                TeamMember("10", "Ana", "ACTIVE"),
                TeamMember("2", "Bruna", "ACTIVE"),
                TeamMember("5", "Ágata", "INACTIVE"),
            ),
        )
        koin.declare(team, allowOverride = true)
    }

    @After
    fun restoreTeam() {
        GlobalContext.get().declare(previous, allowOverride = true)
    }

    @Test
    fun choosingAndClearingASortReordersTheList() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openEmployees(scenario)
            awaitNames(scenario, coreOrder)
            assertNoSortSelected()

            select(R.string.employee_sort_newest)
            awaitNames(scenario, listOf("Ana", "Ágata", "Bruna"))
            onView(withId(R.id.tvFiltroDataFuncionario)).check(matches(withText(R.string.employee_sort_newest)))
            onView(withContentDescription(R.string.employee_sort_clear)).check(matches(isDisplayed()))
            onView(withId(R.id.ivSetaFiltroFuncionario)).check(matches(withEffectiveVisibility(GONE)))

            select(R.string.employee_sort_oldest)
            awaitNames(scenario, listOf("Bruna", "Ágata", "Ana"))

            select(R.string.employee_sort_alphabetical)
            awaitNames(scenario, listOf("Ágata", "Ana", "Bruna"))

            onView(withId(R.id.btnLimparFiltroFuncionario)).perform(click())
            awaitNames(scenario, coreOrder)
            assertNoSortSelected()
        }
    }

    @Test
    fun selectedSortSurvivesActivityRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openEmployees(scenario)
            awaitNames(scenario, coreOrder)
            select(R.string.employee_sort_oldest)
            awaitNames(scenario, listOf("Bruna", "Ágata", "Ana"))

            scenario.recreate()

            awaitNames(scenario, listOf("Bruna", "Ágata", "Ana"))
            onView(withId(R.id.tvFiltroDataFuncionario)).check(matches(withText(R.string.employee_sort_oldest)))
            onView(withId(R.id.btnLimparFiltroFuncionario)).check(matches(isDisplayed()))
        }
    }

    private fun openEmployees(scenario: ActivityScenario<MainActivity>) {
        awaitNavigationReady(scenario, R.id.homeFragment)
        onView(withId(R.id.navigation_employees)).perform(click())
        awaitNavigationReady(scenario, R.id.employeeFragment)
    }

    private fun select(label: Int) {
        onView(withId(R.id.dropdownFiltroDataFuncionario)).perform(click())
        onView(withText(label)).perform(click())
    }

    private fun assertNoSortSelected() {
        onView(withId(R.id.tvFiltroDataFuncionario)).check(matches(withText(R.string.employee_sort_placeholder)))
        onView(withId(R.id.ivSetaFiltroFuncionario)).check(matches(isDisplayed()))
        onView(withId(R.id.btnLimparFiltroFuncionario)).check(matches(withEffectiveVisibility(GONE)))
    }

    private fun awaitNames(scenario: ActivityScenario<MainActivity>, expected: List<String>) {
        var actual = emptyList<String>()
        repeat(ATTEMPTS) {
            scenario.onActivity { activity ->
                val list = activity.findViewById<LinearLayout>(R.id.employeeList)
                actual = (0 until (list?.childCount ?: 0)).mapNotNull { index ->
                    list.getChildAt(index).findViewById<TextView>(R.id.tvNomeFuncionario)?.text?.toString()
                }
            }
            if (actual == expected) return
            Thread.sleep(WAIT_MS)
        }
        assertEquals(expected, actual)
    }

    private companion object {
        const val ATTEMPTS = 50
        const val WAIT_MS = 100L
    }
}
