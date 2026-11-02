package com.quistock.quistock.presentation.profile

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quistock.quistock.domain.model.StoreLocation
import io.mockk.andThenJust
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapsLauncherTests {
    private val context = mockk<Context>()
    private val started = mutableListOf<Intent>()
    private val withCoordinates = StoreLocation("Loja Centro", "Rua A, 10", "Cidade", "SP", -23.5, -46.6)
    private val addressOnly = StoreLocation("Loja Centro", "Rua A, 10", "Cidade", "SP", null, null)

    @Test fun coordinatesOpenTheMapsAppWithTheStoreLabel() {
        every { context.startActivity(capture(started)) } just runs

        assertTrue(MapsLauncher.open(context, withCoordinates))

        val intent = started.single()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals("geo:-23.5,-46.6?q=-23.5,-46.6(Loja%20Centro)", intent.data.toString())
    }

    @Test fun addressOnlyIsSearchedWithoutTheStoreName() {
        assertEquals(
            "geo:0,0?q=Rua%20A%2C%2010%2C%20Cidade%20-%20SP",
            MapsLauncher.nativeUri(addressOnly, addressOnly.mapsQuery!!).toString(),
        )
    }

    @Test fun browserIsTheFallbackWithoutAMapsApp() {
        every { context.startActivity(capture(started)) } throws ActivityNotFoundException() andThenJust runs

        assertTrue(MapsLauncher.open(context, addressOnly))

        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=Rua%20A%2C%2010%2C%20Cidade%20-%20SP",
            started.last().data.toString(),
        )
    }

    @Test fun nothingOpensWithoutMapsAppOrBrowser() {
        every { context.startActivity(any()) } throws ActivityNotFoundException()

        assertFalse(MapsLauncher.open(context, withCoordinates))
        verify(exactly = 2) { context.startActivity(any()) }
    }

    @Test fun storeWithoutAddressOrCoordinatesOpensNothing() {
        val empty = StoreLocation("Loja Centro", null, null, null, null, null)

        assertFalse(MapsLauncher.open(context, empty))
        verify(exactly = 0) { context.startActivity(any()) }
    }
}
