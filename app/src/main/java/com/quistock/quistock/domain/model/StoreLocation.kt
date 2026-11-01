package com.quistock.quistock.domain.model

class StoreLocation(
    val name: String,
    val street: String?,
    val city: String?,
    val state: String?,
    val latitude: Double?,
    val longitude: Double?,
    /** Approximate point found from the address; used only to place the preview map, never the maps-app pin. */
    val previewPoint: GeoPoint? = null,
) {
    /** Where the preview map is centered: exact coordinates when known, else the geocoded address. */
    val mapPoint: GeoPoint?
        get() = if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else previewPoint

    fun withPreview(point: GeoPoint?): StoreLocation =
        StoreLocation(name, street, city, state, latitude, longitude, point)

    val hasCoordinates: Boolean get() = latitude != null && longitude != null

    /** "Street" and "City - ST" lines, omitting blank parts. */
    val addressLines: List<String>
        get() = listOfNotNull(
            street,
            listOfNotNull(city, state).joinToString(" - ").ifBlank { null },
        )

    val hasAddress: Boolean get() = addressLines.isNotEmpty()

    /** Query for a maps app: exact coordinates when valid, otherwise the human address. */
    val mapsQuery: String?
        get() = when {
            hasCoordinates -> "$latitude,$longitude"
            hasAddress -> addressLines.joinToString(", ")
            else -> null
        }
}

class GeoPoint(val latitude: Double, val longitude: Double)

sealed interface StoreLocationResult {
    class Success(val location: StoreLocation) : StoreLocationResult
    data object NoAddress : StoreLocationResult
    data object Failure : StoreLocationResult
}
