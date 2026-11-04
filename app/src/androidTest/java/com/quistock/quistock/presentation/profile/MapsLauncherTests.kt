package com.quistock.quistock.presentation.profile

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quistock.quistock.RecordingContext
import com.quistock.quistock.domain.model.StoreLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapsLauncherTests {
    private val context = RecordingContext()
    private val started = context.started
    private val withCoordinates = StoreLocation("Loja Centro", "Rua A, 10", "Cidade", "SP", -23.5, -46.6)
    private val addressOnly = StoreLocation("Loja Centro", "Rua A, 10", "Cidade", "SP", null, null)

    @Test fun coordinatesOpenTheMapsAppWithTheStoreLabel() {
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
        context.onStart = { if (started.size == 1) throw ActivityNotFoundException() }

        assertTrue(MapsLauncher.open(context, addressOnly))

        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=Rua%20A%2C%2010%2C%20Cidade%20-%20SP",
            started.last().data.toString(),
        )
    }

    @Test fun nothingOpensWithoutMapsAppOrBrowser() {
        context.onStart = { throw ActivityNotFoundException() }

        assertFalse(MapsLauncher.open(context, withCoordinates))
        assertEquals(2, started.size)
    }

    @Test fun storeWithoutAddressOrCoordinatesOpensNothing() {
        val empty = StoreLocation("Loja Centro", null, null, null, null, null)

        assertFalse(MapsLauncher.open(context, empty))
        assertTrue(started.isEmpty())
    }
}
