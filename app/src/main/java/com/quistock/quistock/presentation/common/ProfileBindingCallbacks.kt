package com.quistock.quistock.presentation.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import com.quistock.quistock.R
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.presentation.activity.SessionViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Binds explicitly tagged shared profile slots, without replacing another employee's details. */
class ProfileBindingCallbacks(private val model: SessionViewModel) : FragmentManager.FragmentLifecycleCallbacks() {
    override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
        if (f is NavHostFragment) return
        val slots = descendants(v).filter { view ->
            val tag = view.tag as? String
            tag?.startsWith("current_profile_") == true || tag?.startsWith("current_manager_") == true
        }
        if (slots.isEmpty()) return
        val initial = (model.profile.state.value as? ProfileState.Ready)?.takeIf {
            model.session.state.value == SessionState.Active &&
                it.generation == model.session.snapshot().generation
        }
        // Fill shared slots before the first visible frame.
        slots.forEach {
            // Profile data must not enter Android's persisted view hierarchy state.
            it.isSaveEnabled = false
            bind(it, initial)
        }
        f.viewLifecycleOwner.lifecycleScope.launch {
            f.viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(model.profile.state, model.session.state) { profile, session -> profile to session }
                    .collectLatest { (state, session) ->
                        val ready = (state as? ProfileState.Ready)?.takeIf {
                            session == SessionState.Active && it.generation == model.session.snapshot().generation
                        }
                        slots.forEach { slot -> bind(slot, ready) }
                        if (ready != null) bindPhotos(slots.filterIsInstance<ImageView>(), ready)
                    }
            }
        }
    }

    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
        }
    }

    private fun bind(view: View, ready: ProfileState.Ready?) {
        if ((view.tag as? String)?.startsWith("current_manager_") == true) {
            view.isVisible = ManagerReviewPolicy.canShow(
                ready, model.session.state.value, model.session.snapshot().generation,
            )
            return
        }
        when (view) {
            is ImageView -> {
                view.setImageResource(R.drawable.ic_profile)
                view.contentDescription = ready?.profile?.name ?: view.context.getString(R.string.profile_title)
            }

            is TextView -> bindText(view, ready)
        }
    }

    private fun profileText(view: TextView, ready: ProfileState.Ready?): String {
        val profile = ready?.profile
        return when (view.tag) {
            "current_profile_store" -> profile?.storeName

            "current_profile_role" -> profile?.role?.displayName

            "current_profile_greeting" -> profile?.let {
                view.context.getString(R.string.profile_greeting, it.name)
            }

            "current_profile_email_input" -> profile?.email

            else -> profile?.name
        }.orEmpty()
    }

    private fun bindText(view: TextView, ready: ProfileState.Ready?) {
        val value = profileText(view, ready)
        if (view is EditText) {
            bindInput(view, value, ready?.generation)
        } else {
            view.text = value
            view.contentDescription = value
            if (view.tag != "current_profile_name" && view.tag != "current_profile_role") {
                view.maxLines = 1
                view.ellipsize = TextUtils.TruncateAt.END
            }
        }
    }

    private fun bindInput(view: EditText, value: String, generation: Long?) {
        val marker = view.getTag(R.id.profile_prefill_generation) as? Long
        if (generation == null) {
            view.setText("")
            view.setTag(R.id.profile_prefill_generation, null)
        } else if (marker != generation) {
            // Keep edits in the current view when the shared state is rendered again.
            view.setText(value)
            view.setTag(R.id.profile_prefill_generation, generation)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun bindPhotos(images: List<ImageView>, ready: ProfileState.Ready) {
        if (images.isEmpty()) return
        try {
            val bitmap = model.photoBytes()?.let { decodePhoto(it) }
            if (bitmap != null && isCurrentProfile(ready)) {
                images.forEach { it.setImageBitmap(bitmap) }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Invalid/unavailable image keeps the placeholder; profile navigation is independent.
        }
    }

    private fun isCurrentProfile(ready: ProfileState.Ready): Boolean =
        model.profile.state.value === ready && model.session.state.value == SessionState.Active &&
            model.session.snapshot().generation == ready.generation

    private suspend fun decodePhoto(bytes: ByteArray): Bitmap? = withContext(Dispatchers.Default) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > MAX_PHOTO_DIMENSION || bounds.outHeight / sample > MAX_PHOTO_DIMENSION) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private companion object {
        const val MAX_PHOTO_DIMENSION = 512
    }
}
