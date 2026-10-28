package com.quistock.quistock.presentation.navigation

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ScrollView
import androidx.core.view.doOnPreDraw
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import com.quistock.quistock.R

/** Captures only navigation UI state, never headers, profile fields or credentials. */
internal object TabContent {
    private val filterIds = setOf(R.id.etBuscaFuncionario, R.id.etBuscaProduto)

    fun capture(root: View): TabContentState {
        val views = descendants(root)
        val scroll = views.filter { it is ScrollView || it is NestedScrollView }
            .associate { it.id to it.scrollY }
        val lists = views.filterIsInstance<RecyclerView>()
            .associate { it.id to it.layoutManager?.onSaveInstanceState() }
        val inputs = views.filterIsInstance<EditText>().filter { it.id in filterIds }
            .associate { it.id to it.text.toString() }
        return TabContentState(scroll, lists, inputs)
    }

    fun restore(root: View, state: TabContentState, owner: LifecycleOwner) {
        state.inputs.forEach { (id, text) -> root.findViewById<EditText>(id)?.setText(text) }
        root.doOnPreDraw {
            state.scroll.forEach { (id, y) -> root.findViewById<View>(id)?.scrollTo(0, y) }
        }
        state.lists.forEach { (id, saved) ->
            root.findViewById<RecyclerView>(id)?.let { ListPositionRestorer(it, saved, owner) }
        }
    }

    fun reselect(root: View, tab: MainTab) {
        descendants(root).forEach { view ->
            when (view) {
                is ScrollView -> {
                    val y = if (tab == MainTab.CHATBOT) view.getChildAt(0)?.height ?: 0 else 0
                    view.scrollTo(0, y)
                }
                is NestedScrollView -> view.scrollTo(0, 0)
                is RecyclerView -> {
                    (view.getTag(R.id.navigation_position_restorer) as? ListPositionRestorer)?.cancel()
                    view.setTag(R.id.navigation_position_restorer, null)
                    view.scrollToPosition(0)
                }
            }
        }
    }

    fun disableDiskState(root: View) {
        descendants(root).forEach { it.isSaveEnabled = false }
    }

    fun descendants(root: View): List<View> = buildList {
        add(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) addAll(descendants(root.getChildAt(index)))
        }
    }
}
