package com.quistock.quistock.presentation.profile

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.transition.TransitionManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.quistock.quistock.R
import com.quistock.quistock.databinding.ViewStoreLocationBinding
import com.quistock.quistock.domain.model.GeoPoint
import com.quistock.quistock.domain.model.StoreLocation

/** Renders [StoreLocationState] into the Profile "Localização" card and owns the preview map lifecycle. */
class StoreLocationSection(
    private val binding: ViewStoreLocationBinding,
    private val onOpen: (StoreLocation) -> Unit,
    private val onRetry: () -> Unit,
) {
    private var location: StoreLocation? = null
    private var pulse: ObjectAnimator? = null

    init {
        val open = View.OnClickListener { location?.let(onOpen) }
        binding.locationCard.setOnClickListener(open)
        binding.locationOpenButton.setOnClickListener(open)
        binding.locationRetryButton.setOnClickListener { onRetry() }
        render(StoreLocationState.Loading)
    }

    fun attach(owner: LifecycleOwner) {
        val map = binding.locationMap
        map.onCreate(null)
        owner.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = map.onStart()
                override fun onResume(owner: LifecycleOwner) = map.onResume()
                override fun onPause(owner: LifecycleOwner) = map.onPause()
                override fun onStop(owner: LifecycleOwner) = map.onStop()
                override fun onDestroy(owner: LifecycleOwner) {
                    stopPulse()
                    map.onDestroy()
                }
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
        store.mapPoint?.let { showMap(store, it) }
    }

    private fun showMap(store: StoreLocation, point: GeoPoint) {
        val position = LatLng(point.latitude, point.longitude)
        binding.locationMap.isVisible = true
        binding.locationPinPlaceholder.isVisible = false
        binding.locationMap.getMapAsync { map ->
            map.uiSettings.setAllGesturesEnabled(false)
            map.uiSettings.isMapToolbarEnabled = false
            map.setMapStyle(MapStyleOptions.loadRawResourceStyle(binding.root.context, R.raw.map_style_dark_purple))
            map.clear()
            map.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(store.name)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)),
            )
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(position, MAP_ZOOM))
            map.setOnMapClickListener { onOpen(store) }
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
        const val MAP_ZOOM = 16f
        const val PULSE_MIN_ALPHA = 0.35f
        const val PULSE_MILLIS = 900L
    }
}
