package com.quistock.quistock.presentation.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quistock.quistock.AuthenticatedSessionRule
import com.quistock.quistock.R
import com.quistock.quistock.databinding.ViewStoreLocationBinding
import com.quistock.quistock.domain.model.GeoPoint
import com.quistock.quistock.domain.model.StoreLocation
import com.quistock.quistock.domain.model.distanceMetersTo
import com.quistock.quistock.presentation.activity.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class StoreLocationSectionTests {
    @get:Rule val authenticatedSession = AuthenticatedSessionRule()
    private val opened = mutableListOf<StoreLocation>()
    private var retries = 0
    private var userActions = 0
    private val store = StoreLocation("Loja Centro", "Rua A, 10", "Cidade", "SP", -23.5505, -46.6333)
    private val nearUser = GeoPoint(-23.5506, -46.6334)
    private val farUser = GeoPoint(-23.5600, -46.6500)

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
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var binding: ViewStoreLocationBinding
            lateinit var section: StoreLocationSection
            scenario.onActivity { activity ->
                binding = ViewStoreLocationBinding.inflate(LayoutInflater.from(activity))
                activity.addContentView(
                    binding.root,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                )
                section = StoreLocationSection(binding, { opened += it }, { retries++ })
                section.attach(activity)
                section.render(StoreLocationState.Ready(store))
            }
            awaitMap(scenario, binding)
            scenario.onActivity { section.renderUser(UserLocationState.Located(farUser)) }
            awaitMap(scenario, binding)
            scenario.onActivity { section.renderUser(UserLocationState.Located(nearUser)) }
            awaitMap(scenario, binding)

            assertTrue(binding.locationMap.isVisible)
            assertTrue(opened.isEmpty())
        }
    }

    /** Map callbacks run in registration order, so a later callback means the section's ones already ran. */
    private fun awaitMap(scenario: ActivityScenario<MainActivity>, binding: ViewStoreLocationBinding) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val ready = CountDownLatch(1)
        scenario.onActivity { binding.locationMap.getMapAsync { ready.countDown() } }
        assertTrue("Map was not ready", ready.await(MAP_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun text(binding: ViewStoreLocationBinding, id: Int) = binding.root.context.getString(id)

    private fun withSection(test: (StoreLocationSection, ViewStoreLocationBinding) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val binding = ViewStoreLocationBinding.inflate(LayoutInflater.from(activity))
                val section = StoreLocationSection(binding, { opened += it }, { retries++ }, { userActions++ })
                test(section, binding)
            }
        }
    }

    private companion object {
        const val MAP_TIMEOUT_SECONDS = 20L
    }
}
