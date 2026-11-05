package com.quistock.quistock.presentation.employee

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.model.TeamMember
import com.quistock.quistock.domain.model.TeamResult
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.port.TeamRepository
import com.quistock.quistock.domain.usecase.ProfileUseCase
import com.quistock.quistock.domain.usecase.SessionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

class TeamViewModel(
    private val repository: TeamRepository,
    private val session: SessionUseCase,
    private val profile: ProfileUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(TeamState())
    val state = mutableState.asStateFlow()
    private val mutableSort = MutableStateFlow<TeamSort?>(null)
    val sort = mutableSort.asStateFlow()

    fun setSort(value: TeamSort?) {
        mutableSort.value = value
    }

    fun load() {
        if (mutableState.value.busy) return
        val expected = session.snapshot()
        if (!allowed(expected.generation)) return
        mutableState.value = TeamState(busy = true)
        viewModelScope.launch {
            val result = repository.list(expected)
            if (allowed(expected.generation)) mutableState.value = TeamState(result = result)
        }
    }

    fun create(name: String, email: String) {
        if (mutableState.value.busy) return
        val expected = session.snapshot()
        val storeId = (profile.state.value as? ProfileState.Ready)?.profile?.storeId
        if (storeId == null || !allowed(expected.generation)) return
        mutableState.value = TeamState(busy = true)
        viewModelScope.launch {
            val result = repository.create(name.trim(), email.trim(), storeId, expected)
            if (allowed(expected.generation)) mutableState.value = TeamState(result = result)
        }
    }

    private fun allowed(generation: Long): Boolean {
        val ready = profile.state.value as? ProfileState.Ready ?: return false
        return session.state.value == SessionState.Active && session.snapshot().generation == generation &&
            ready.generation == generation && ready.profile.role == UserRole.MANAGER
    }
}

data class TeamState(val busy: Boolean = false, val result: TeamResult? = null)

enum class TeamSort { NEWEST, OLDEST, ALPHABETICAL }

fun visibleMembers(members: List<TeamMember>, query: String, sort: TeamSort?): List<TeamMember> {
    val matching = members.filter { it.name.contains(query.trim(), ignoreCase = true) }
    return when (sort) {
        null -> matching
        TeamSort.NEWEST -> matching.sortedWith(compareBy(nullsLast(reverseOrder())) { it.id.toLongOrNull() })
        TeamSort.OLDEST -> matching.sortedWith(compareBy(nullsLast(naturalOrder())) { it.id.toLongOrNull() })
        TeamSort.ALPHABETICAL -> matching.sortedWith(compareBy(portugueseCollator()) { it.name })
    }
}

private fun portugueseCollator(): Collator =
    Collator.getInstance(Locale.forLanguageTag("pt-BR")).apply { strength = Collator.SECONDARY }
