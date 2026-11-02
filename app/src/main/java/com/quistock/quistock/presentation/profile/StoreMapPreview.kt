package com.quistock.quistock.presentation.profile

import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.doOnLayout
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.quistock.quistock.R
import com.quistock.quistock.domain.model.GeoPoint
import com.quistock.quistock.domain.model.StoreLocation
import com.quistock.quistock.domain.model.distanceMetersTo

/** Draws the store pin and, when known, the user's dot on the lite preview map, framing both. */
internal class StoreMapPreview(private val mapView: MapView) {
    private var userIcon: BitmapDescriptor? = null

    fun show(store: StoreLocation, storePoint: GeoPoint, user: GeoPoint?, onClick: () -> Unit) {
        // Bounds need the map size, which is only known once the (previously gone) view is laid out.
        mapView.doOnLayout {
            mapView.getMapAsync { map ->
                val context = mapView.context
                map.uiSettings.setAllGesturesEnabled(false)
                map.uiSettings.isMapToolbarEnabled = false
                map.setMapStyle(MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark_purple))
                map.clear()
                map.addMarker(
                    MarkerOptions()
                        .position(storePoint.toLatLng())
                        .title(store.name)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)),
                )
                user?.let {
                    map.addMarker(
                        MarkerOptions()
                            .position(it.toLatLng())
                            .title(context.getString(R.string.user_location_marker))
                            .anchor(CENTER, CENTER)
                            .icon(userIcon()),
                    )
                }
                map.moveCamera(camera(storePoint, user))
                map.setOnMapClickListener { onClick() }
            }
        }
    }

    private fun camera(store: GeoPoint, user: GeoPoint?): CameraUpdate {
        val width = mapView.width
        val height = mapView.height
        val padding = (BOUNDS_PADDING_DP * mapView.resources.displayMetrics.density).toInt()
        val fits = width > 2 * padding && height > 2 * padding
        // Points a few meters apart would zoom in past street level; keep the store view instead.
        if (user == null || !fits || store.distanceMetersTo(user) < MIN_BOUNDS_METERS) {
            return CameraUpdateFactory.newLatLngZoom(store.toLatLng(), MAP_ZOOM)
        }
        val bounds = LatLngBounds.builder().include(store.toLatLng()).include(user.toLatLng()).build()
        return CameraUpdateFactory.newLatLngBounds(bounds, width, height, padding)
    }

    private fun userIcon(): BitmapDescriptor = userIcon ?: BitmapDescriptorFactory.fromBitmap(
        requireNotNull(ContextCompat.getDrawable(mapView.context, R.drawable.ic_user_location_dot)).toBitmap(),
    ).also { userIcon = it }

    private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)

    private companion object {
        const val MAP_ZOOM = 16f
        const val CENTER = 0.5f
        const val BOUNDS_PADDING_DP = 28
        const val MIN_BOUNDS_METERS = 150.0
    }
}
