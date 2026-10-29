package com.quistock.quistock.presentation.navigation

/** Deliberate visits are retained; Back consumes them rather than generating a new visit. */
internal class TabHistory {
    private val visits = mutableListOf<MainTab>()

    fun visit(tab: MainTab) {
        if (visits.lastOrNull() != tab) visits.add(tab)
    }

    fun previous(): MainTab? = if (visits.size > 1) {
        visits.removeAt(visits.lastIndex)
        visits.last()
    } else {
        null
    }

    fun clear() = visits.clear()
}
