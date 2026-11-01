package com.quistock.quistock.presentation.profile

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.quistock.quistock.domain.model.StoreLocation

/** Opens a store in the installed maps app, falling back to the browser. */
object MapsLauncher {
    fun open(context: Context, location: StoreLocation): Boolean {
        val query = location.mapsQuery ?: return false
        return start(context, nativeUri(location, query)) || start(context, webUri(query))
    }

    internal fun nativeUri(location: StoreLocation, query: String): Uri = if (location.hasCoordinates) {
        // Coordinates plus label place the pin exactly on the store.
        Uri.parse("geo:$query?q=$query(${Uri.encode(location.name)})")
    } else {
        Uri.parse("geo:0,0?q=${Uri.encode(query)}")
    }

    internal fun webUri(query: String): Uri =
        Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}")

    private fun start(context: Context, uri: Uri): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
