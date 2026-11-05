package com.quistock.quistock.presentation.profile

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import androidx.core.view.isVisible
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.quistock.quistock.R
import com.quistock.quistock.databinding.ViewStoreLocationBinding
import com.quistock.quistock.domain.model.GeoPoint
import com.quistock.quistock.domain.model.StoreLocation
import com.quistock.quistock.domain.model.distanceMetersTo
import io.mockk.clearMocks
import io.mockk.clearStaticMockk
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoreLocationSectionTests {
    private val opened = mutableListOf<StoreLocation>()
    private var retries = 0
    private var userActions = 0
    private val store = StoreLocation("Loja Centro", "Rua A, 10", "Cidade", "SP", -23.5505, -46.6333)
    private val nearUser = GeoPoint(-23.5506, -46.6334)
    private val farUser = GeoPoint(-23.5600, -46.6500)

    @Before fun clearPreviousMockInteractions() {
        clearMocks(map, answers = false)
        clearStaticMockk(BitmapDescriptorFactory::class, CameraUpdateFactory::class, answers = false)
        cameraMove.clear()
    }

    @Test fun loadingShowsTheSkeletonAndCannotBeOpened() = withSection { _, binding ->
        assertTrue(binding.locationSkeleton.isVisible)
        assertFalse(binding.locationPinPlaceholder.isVisible)
        assertFalse(binding.locationDistance.isVisible)
        assertFalse(binding.locationCard.isClickable)
        binding.locationCard.performClick()
        assertTrue(opened.isEmpty())
    }

    @Test fun missingAddressAndFailureExplainWhyAndOnlyFailureRetries() = withSection { section, binding ->
        section.render(StoreLocationState.NoAddress)
        assertEquals(text(binding, R.string.location_no_address), binding.locationMessage.text.toString())
        assertFalse(binding.locationRetryButton.isVisible)
        assertFalse(binding.locationOpenButton.isVisible)

        section.render(StoreLocationState.Failure)
        assertEquals(text(binding, R.string.location_failure), binding.locationMessage.text.toString())
        assertTrue(binding.locationRetryButton.isVisible)
        binding.locationRetryButton.performClick()
        assertEquals(1, retries)
        assertFalse(binding.locationSkeleton.isVisible)
    }

    @Test fun readyStoreShowsAddressMapAndOpensFromCardAndButton() = withSection { section, binding ->
        section.render(StoreLocationState.Ready(store))

        assertEquals("Rua A, 10", binding.locationStreet.text.toString())
        assertEquals("Cidade - SP", binding.locationCity.text.toString())
        assertTrue(binding.locationCity.isVisible)
        assertTrue(binding.locationMap.isVisible)
        assertFalse(binding.locationPinPlaceholder.isVisible)
        assertTrue(binding.locationCard.isClickable)
        assertNotNull(binding.locationCard.contentDescription)
        assertEquals(text(binding, R.string.user_location_locating), binding.locationDistance.text.toString())
        binding.locationCard.performClick()
        binding.locationOpenButton.performClick()
        assertEquals(listOf(store, store), opened)
    }

    @Test fun storeWithoutAddressOrPointShowsItsNameAndNoDistance() = withSection { section, binding ->
        section.render(StoreLocationState.Ready(StoreLocation("Loja Centro", "Rua A, 10", null, null, null, null)))
        assertEquals("Rua A, 10", binding.locationStreet.text.toString())
        assertFalse(binding.locationCity.isVisible)

        section.render(StoreLocationState.Ready(StoreLocation("Loja Centro", null, null, null, null, null)))
        section.renderUser(UserLocationState.Located(nearUser))

        assertEquals("Loja Centro", binding.locationStreet.text.toString())
        assertFalse(binding.locationMap.isVisible)
        assertTrue(binding.locationPinPlaceholder.isVisible)
        assertFalse(binding.locationDistance.isVisible)
    }

    @Test fun userStatesDescribeTheDistanceAndOnlyProblemsAreActionable() = withSection { section, binding ->
        section.render(StoreLocationState.Ready(store))
        val row = binding.locationDistance

        section.renderUser(UserLocationState.PermissionNeeded)
        assertEquals(text(binding, R.string.user_location_permission), row.text.toString())
        assertTrue(row.isClickable)
        row.performClick()

        section.renderUser(UserLocationState.Unavailable)
        assertEquals(text(binding, R.string.user_location_unavailable), row.text.toString())
        assertTrue(row.isClickable)
        row.performClick()
        assertEquals(2, userActions)

        val located = UserLocationState.Located(farUser)
        section.renderUser(located)
        section.renderUser(located)
        val distance = DistanceFormatter.format(GeoPoint(-23.5505, -46.6333).distanceMetersTo(farUser))
        assertEquals(binding.root.context.getString(R.string.user_location_distance, distance), row.text.toString())
        assertFalse(row.isClickable)
    }

    @Test fun geocodedStorePointGivesAnApproximateDistance() = withSection { section, binding ->
        val geocoded = StoreLocation("Loja Centro", "Rua A, 10", "Cidade", "SP", null, null)
            .withPreview(GeoPoint(-23.5505, -46.6333))
        section.render(StoreLocationState.Ready(geocoded))
        section.renderUser(UserLocationState.Located(farUser))

        val distance = DistanceFormatter.format(GeoPoint(-23.5505, -46.6333).distanceMetersTo(farUser))
        assertEquals(
            binding.root.context.getString(R.string.user_location_distance_approximate, distance),
            binding.locationDistance.text.toString(),
        )
    }

    @Test fun userPositionWithoutAStoreIsNotShown() = withSection { section, binding ->
        section.render(StoreLocationState.Failure)
        section.renderUser(UserLocationState.Located(nearUser))

        assertFalse(binding.locationDistance.isVisible)
        assertNull(binding.locationCard.contentDescription)
    }

    @Test fun previewMapFramesTheStoreAndUserWhenAttached() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = ContextThemeWrapper(
                InstrumentationRegistry.getInstrumentation().targetContext,
                R.style.Theme_QuiStock,
            )
            val binding = inflateBinding(context)
            val section = StoreLocationSection(binding, { opened += it }, { retries++ }, { userActions++ })
            val owner = object : androidx.lifecycle.LifecycleOwner {
                val registry = androidx.lifecycle.LifecycleRegistry(this)
                override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
            }
            section.attach(owner)
            owner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_CREATE)
            owner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_START)
            owner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_RESUME)
            try {
                section.render(StoreLocationState.Ready(store))
                binding.locationMap.layout(0, 0, MAP_WIDTH, MAP_HEIGHT)
                section.renderUser(UserLocationState.Located(farUser))
                binding.locationMap.layout(0, 0, MAP_WIDTH, MAP_HEIGHT)
                section.renderUser(UserLocationState.Located(nearUser))
                binding.locationMap.layout(0, 0, MAP_WIDTH, MAP_HEIGHT)
                assertTrue(binding.locationMap.isVisible)
                verify(exactly = 1) { CameraUpdateFactory.newLatLngBounds(any(), MAP_WIDTH, MAP_HEIGHT, any()) }
                verify(exactly = 2) { CameraUpdateFactory.newLatLngZoom(any(), any()) }

                // Interactive: gestures on, and a tap on the map stays in the app.
                verify { map.uiSettings.setAllGesturesEnabled(true) }
                verify(exactly = 0) { map.setOnMapClickListener(any()) }
                assertTrue(opened.isEmpty())

                // A new read keeps the view the user chose, and both markers stay drawn.
                cameraMove.captured.onCameraMoveStarted(GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE)
                clearMocks(map, answers = false)
                section.renderUser(UserLocationState.Located(farUser))
                binding.locationMap.layout(0, 0, MAP_WIDTH, MAP_HEIGHT)
                verify(exactly = 2) { map.addMarker(any()) }
                verify(exactly = 0) { map.moveCamera(any()) }
            } finally {
                owner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_PAUSE)
                owner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_STOP)
                owner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_DESTROY)
            }
            assertEquals(1, (binding.locationMap as TestMapView).destroyCalls)
        }
    }

    @Test fun touchesOnTheMapAreKeptFromTheScrollingScreen() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val requests = mutableListOf<Boolean>()
            val parent = object : android.widget.FrameLayout(context) {
                override fun requestDisallowInterceptTouchEvent(disallow: Boolean) {
                    requests += disallow
                }
            }
            val frame = MapTouchFrameLayout(context)
            parent.addView(frame)
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL).forEach { action ->
                val event = MotionEvent.obtain(0L, 0L, action, 0f, 0f, 0)
                frame.dispatchTouchEvent(event)
                event.recycle()
            }
            assertEquals(listOf(true, false, false), requests)
        }
    }

    private fun text(binding: ViewStoreLocationBinding, id: Int) = binding.root.context.getString(id)

    private fun withSection(test: (StoreLocationSection, ViewStoreLocationBinding) -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = ContextThemeWrapper(
                InstrumentationRegistry.getInstrumentation().targetContext,
                R.style.Theme_QuiStock,
            )
            val binding = inflateBinding(context)
            val section = StoreLocationSection(binding, { opened += it }, { retries++ }, { userActions++ })
            test(section, binding)
        }
    }

    private fun inflateBinding(context: android.content.Context): ViewStoreLocationBinding {
        val inflater = LayoutInflater.from(context).cloneInContext(context)
        inflater.factory2 = object : LayoutInflater.Factory2 {
            override fun onCreateView(
                parent: android.view.View?,
                name: String,
                context: android.content.Context,
                attrs: android.util.AttributeSet,
            ): android.view.View? = onCreateView(name, context, attrs)

            override fun onCreateView(
                name: String,
                context: android.content.Context,
                attrs: android.util.AttributeSet,
            ): android.view.View? = if (name == MapView::class.java.name) TestMapView(context, attrs) else null
        }
        return ViewStoreLocationBinding.inflate(inflater)
    }

    private class TestMapView(context: android.content.Context, attrs: android.util.AttributeSet) :
        MapView(context, attrs) {
        var destroyCalls = 0

        override fun onCreate(state: android.os.Bundle?) = Unit
        override fun onStart() = Unit
        override fun onResume() = Unit
        override fun onPause() = Unit
        override fun onStop() = Unit
        override fun onDestroy() {
            destroyCalls++
        }

        override fun getMapAsync(callback: OnMapReadyCallback) = callback.onMapReady(map)
    }

    companion object {
        private val map = mockk<GoogleMap>(relaxed = true)
        private val cameraMove = slot<GoogleMap.OnCameraMoveStartedListener>()
        const val MAP_WIDTH = 500
        const val MAP_HEIGHT = 300

        @JvmStatic
        @BeforeClass
        fun mockMapsOnce() {
            mockkStatic(BitmapDescriptorFactory::class)
            mockkStatic(CameraUpdateFactory::class)
            every { BitmapDescriptorFactory.defaultMarker(any()) } returns mockk(relaxed = true)
            every { BitmapDescriptorFactory.fromBitmap(any()) } returns mockk(relaxed = true)
            every { CameraUpdateFactory.newLatLngZoom(any(), any()) } returns mockk()
            every { CameraUpdateFactory.newLatLngBounds(any(), any(), any(), any()) } returns mockk()
            every { map.setOnCameraMoveStartedListener(capture(cameraMove)) } just runs
        }

        @JvmStatic
        @AfterClass
        fun restoreMapHooks() {
            unmockkStatic(BitmapDescriptorFactory::class, CameraUpdateFactory::class)
        }
    }
}
