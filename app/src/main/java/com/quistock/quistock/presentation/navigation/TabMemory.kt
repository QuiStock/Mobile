package com.quistock.quistock.presentation.navigation

import android.os.Parcelable
import androidx.lifecycle.ViewModel
import com.quistock.quistock.domain.model.UserRole

/** Activity memory only: never supplied to SavedStateHandle or saved instance state. */
internal class TabMemory : ViewModel() {
    val history = TabHistory()
    val content = mutableMapOf<MainTab, TabContentState>()
    private var identity: Pair<Long, UserRole>? = null

    fun bind(generation: Long, role: UserRole) {
        val next = generation to role
        if (identity != next) {
            clear()
            identity = next
            history.visit(MainTab.HOME)
        }
    }

    fun clear() {
        identity = null
        history.clear()
        content.clear()
    }
}

internal data class TabContentState(
    val scroll: Map<Int, Int>,
    val lists: Map<Int, Parcelable?>,
    val inputs: Map<Int, String>,
)
