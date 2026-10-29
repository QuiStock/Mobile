package com.quistock.quistock.presentation.navigation

import android.os.Parcelable
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import com.quistock.quistock.R

/** Home data can arrive after its view; restore after data instead of consuming an empty layout. */
internal class ListPositionRestorer(
    private val list: RecyclerView,
    private val saved: Parcelable?,
    owner: LifecycleOwner,
) : RecyclerView.AdapterDataObserver(),
    DefaultLifecycleObserver {
    private val adapter = list.adapter
    private var restored = false
    private var observing = false

    init {
        owner.lifecycle.addObserver(this)
        adapter?.registerAdapterDataObserver(this)
        observing = adapter != null
        (list.getTag(R.id.navigation_position_restorer) as? ListPositionRestorer)?.cancel()
        list.setTag(R.id.navigation_position_restorer, this)
        restoreWhenReady()
    }

    override fun onChanged() = restoreWhenReady()

    override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = restoreWhenReady()

    override fun onDestroy(owner: LifecycleOwner) {
        cancel()
    }

    fun cancel() {
        restored = true
        stopObserving()
        if (list.getTag(R.id.navigation_position_restorer) === this) {
            list.setTag(R.id.navigation_position_restorer, null)
        }
    }

    private fun stopObserving() {
        if (observing) {
            adapter?.unregisterAdapterDataObserver(this)
            observing = false
        }
    }

    private fun restoreWhenReady() {
        if (!restored && (list.adapter?.itemCount ?: 0) > 0) {
            restored = true
            stopObserving()
            list.doOnPreDraw {
                if (list.getTag(R.id.navigation_position_restorer) === this) {
                    list.layoutManager?.onRestoreInstanceState(saved)
                }
            }
        }
    }
}
