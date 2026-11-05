package com.quistock.quistock.presentation.profile

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.transition.TransitionManager
import com.quistock.quistock.R
import com.quistock.quistock.databinding.ViewStoreLocationBinding
import com.quistock.quistock.domain.model.StoreLocation
import com.quistock.quistock.domain.model.distanceMetersTo

/**
 * Renders [StoreLocationState] and [UserLocationState] into the Profile "Localização" card and owns the
 * interactive map lifecycle. [onUserLocationAction] runs when the user taps the permission or retry line.
 */
class StoreLocationSection(
    private val binding: ViewStoreLocationBinding,
    private val onOpen: (StoreLocation) -> Unit,
    private val onRetry: () -> Unit,
    private val onUserLocationAction: () -> Unit = {},
) {
    private var location: StoreLocation? = null
    private var user: UserLocationState = UserLocationState.Locating
    private var lifecycle: Lifecycle? = null
    private val preview = StoreMapPreview(binding.locationMap)
    private var pulse: ObjectAnimator? = null
    private val mapObserver = object : DefaultLifecycleObserver {
        override fun onCreate(owner: LifecycleOwner) = binding.locationMap.onCreate(null)
        override fun onStart(owner: LifecycleOwner) = binding.locationMap.onStart()
        override fun onResume(owner: LifecycleOwner) = binding.locationMap.onResume()
        override fun onPause(owner: LifecycleOwner) = binding.locationMap.onPause()
        override fun onStop(owner: LifecycleOwner) = binding.locationMap.onStop()
        override fun onDestroy(owner: LifecycleOwner) = binding.locationMap.onDestroy()
    }

    init {
        val open = View.OnClickListener { location?.let(onOpen) }
        binding.locationCard.setOnClickListener(open)
        binding.locationOpenButton.setOnClickListener(open)
        binding.locationRetryButton.setOnClickListener { onRetry() }
        binding.locationDistance.setOnClickListener { onUserLocationAction() }
        render(StoreLocationState.Loading)
    }

    fun attach(owner: LifecycleOwner) {
        lifecycle = owner.lifecycle
        owner.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) = stopPulse()
            },
        )
    }

    fun render(state: StoreLocationState) {
        TransitionManager.beginDelayedTransition(binding.locationCard)
        location = (state as? StoreLocationState.Ready)?.location
        resetVisibility()
        when (state) {
            StoreLocationState.Loading -> showLoading()
            is StoreLocationState.Ready -> showReady(state.location)
            StoreLocationState.NoAddress -> showMessage(R.string.location_no_address, retry = false)
            StoreLocationState.Failure -> showMessage(R.string.location_failure, retry = true)
        }
        binding.locationCard.isClickable = location != null
        binding.locationCard.isFocusable = location != null
        renderDistance()
    }

    /** Adds the user's position to the preview and shows the distance to the store. */
    fun renderUser(state: UserLocationState) {
        val previous = (user as? UserLocationState.Located)?.point
        user = state
        val store = location
        val storePoint = store?.mapPoint
        val point = (state as? UserLocationState.Located)?.point
        if (store != null && storePoint != null && point !== previous) {
            preview.show(store, storePoint, point)
        }
        renderDistance()
    }

    /** Distance line, only when the store has a point on the map to measure from. */
    private fun renderDistance() {
        val store = location
        val storePoint = store?.mapPoint
        val row = binding.locationDistance
        row.isVisible = storePoint != null
        if (store == null || storePoint == null) return
        val context = binding.root.context
        val state = user
        row.text = when (state) {
            UserLocationState.PermissionNeeded -> context.getString(R.string.user_location_permission)

            UserLocationState.Locating -> context.getString(R.string.user_location_locating)

            UserLocationState.Unavailable -> context.getString(R.string.user_location_unavailable)

            // Without stored coordinates the store point is the geocoded address, so the distance is approximate.
            is UserLocationState.Located -> context.getString(
                if (store.hasCoordinates) {
                    R.string.user_location_distance
                } else {
                    R.string.user_location_distance_approximate
                },
                DistanceFormatter.format(storePoint.distanceMetersTo(state.point)),
            )
        }
        val actionable = state == UserLocationState.PermissionNeeded || state == UserLocationState.Unavailable
        row.isClickable = actionable
        row.isFocusable = actionable
    }

    private fun resetVisibility() {
        stopPulse()
        listOf(
            binding.locationSkeleton,
            binding.locationMap,
            binding.locationStreet,
            binding.locationCity,
            binding.locationMessage,
            binding.locationOpenButton,
            binding.locationRetryButton,
        ).forEach { it.isVisible = false }
        binding.locationPinPlaceholder.isVisible = true
        binding.locationCard.contentDescription = null
    }

    private fun showLoading() {
        binding.locationSkeleton.isVisible = true
        binding.locationPinPlaceholder.isVisible = false
        startPulse()
    }

    private fun showMessage(text: Int, retry: Boolean) {
        binding.locationMessage.setText(text)
        binding.locationMessage.isVisible = true
        binding.locationRetryButton.isVisible = retry
    }

    private fun showReady(store: StoreLocation) {
        val lines = store.addressLines
        binding.locationStreet.text = lines.getOrNull(0) ?: store.name
        binding.locationStreet.isVisible = true
        binding.locationCity.text = lines.getOrNull(1).orEmpty()
        binding.locationCity.isVisible = lines.size > 1
        binding.locationOpenButton.isVisible = true
        binding.locationCard.contentDescription =
            binding.root.context.getString(R.string.location_open_description, store.name)
        store.mapPoint?.let { point ->
            binding.locationMap.isVisible = true
            binding.locationPinPlaceholder.isVisible = false
            lifecycle?.addObserver(mapObserver)
            preview.show(store, point, (user as? UserLocationState.Located)?.point)
        }
    }

    private fun startPulse() {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        pulse = ObjectAnimator.ofFloat(binding.locationSkeleton, View.ALPHA, 1f, PULSE_MIN_ALPHA).apply {
            duration = PULSE_MILLIS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun stopPulse() {
        pulse?.cancel()
        pulse = null
        binding.locationSkeleton.alpha = 1f
    }

    private companion object {
        const val PULSE_MIN_ALPHA = 0.35f
        const val PULSE_MILLIS = 900L
    }
}
