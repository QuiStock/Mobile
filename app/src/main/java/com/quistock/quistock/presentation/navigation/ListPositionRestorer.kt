package com.quistock.quistock.presentation.navigation

import android.os.Parcelable
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView

/** Home data can arrive after its view; restore after data instead of consuming an empty layout. */
internal class ListPositionRestorer(
    private val list: RecyclerView,
    private val saved: Parcelable?,
    owner: LifecycleOwner,
) : RecyclerView.AdapterDataObserver(), DefaultLifecycleObserver {
    private val adapter = list.adapter
    private var restored = false

    init {
        owner.lifecycle.addObserver(this)
        adapter?.registerAdapterDataObserver(this)
        restoreWhenReady()
    }

    override fun onChanged() = restoreWhenReady()

    override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = restoreWhenReady()

    override fun onDestroy(owner: LifecycleOwner) {
        adapter?.unregisterAdapterDataObserver(this)
    }

    private fun restoreWhenReady() {
        if (!restored && list.adapter?.itemCount != 0) {
            restored = true
            adapter?.unregisterAdapterDataObserver(this)
            list.doOnPreDraw { list.layoutManager?.onRestoreInstanceState(saved) }
        }
    }
}
