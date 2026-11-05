package com.quistock.quistock.presentation.employee

import com.quistock.quistock.domain.model.TeamMember
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TeamSortTests {
    private val members = listOf(
        TeamMember("2", "Bruna", "ACTIVE"),
        TeamMember("10", "Ana", "INACTIVE"),
        TeamMember("5", "Ágata", "ACTIVE"),
    )

    private fun names(query: String, sort: TeamSort?) = visibleMembers(members, query, sort).map { it.name }

    @Test fun withoutSortKeepsTheOrderReceivedFromCore() {
        assertEquals(listOf("Bruna", "Ana", "Ágata"), names("", null))
    }

    @Test fun newestListsTheHighestNumericIdFirst() {
        assertEquals(listOf("Ana", "Ágata", "Bruna"), names("", TeamSort.NEWEST))
    }

    @Test fun oldestListsTheLowestNumericIdFirst() {
        assertEquals(listOf("Bruna", "Ágata", "Ana"), names("", TeamSort.OLDEST))
    }

    @Test fun alphabeticalTreatsAccentedNamesAsPortuguese() {
        assertEquals(listOf("Ágata", "Ana", "Bruna"), names("", TeamSort.ALPHABETICAL))
    }

    @Test fun alphabeticalIgnoresCase() {
        val mixed = listOf(TeamMember("1", "bruna", "ACTIVE"), TeamMember("2", "Carla", "ACTIVE"))
        assertEquals(
            listOf("bruna", "Carla"),
            visibleMembers(mixed.reversed(), "", TeamSort.ALPHABETICAL).map { it.name },
        )
    }

    @Test fun nameSearchAppliesTogetherWithSort() {
        assertEquals(listOf("Bruna", "Ana"), names("  N ", TeamSort.OLDEST))
    }

    @Test fun idsThatAreNotNumericGoLastInBothDirections() {
        val unusual = listOf(
            TeamMember("x", "Sem id", "ACTIVE"),
            TeamMember("1", "Um", "ACTIVE"),
            TeamMember("2", "Dois", "ACTIVE"),
        )
        assertEquals(listOf("Dois", "Um", "Sem id"), visibleMembers(unusual, "", TeamSort.NEWEST).map { it.name })
        assertEquals(listOf("Um", "Dois", "Sem id"), visibleMembers(unusual, "", TeamSort.OLDEST).map { it.name })
    }

    @Test fun viewModelStartsWithoutSortAndCanClearTheSelection() {
        val model = TeamViewModel(mockk(), mockk(), mockk())
        assertNull(model.sort.value)
        model.setSort(TeamSort.NEWEST)
        assertEquals(TeamSort.NEWEST, model.sort.value)
        model.setSort(null)
        assertNull(model.sort.value)
    }
}
