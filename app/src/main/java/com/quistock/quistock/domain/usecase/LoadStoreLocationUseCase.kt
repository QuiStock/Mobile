package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.StoreLocationResult
import com.quistock.quistock.domain.port.AddressGeocoder
import com.quistock.quistock.domain.port.StoreLocationRepository

class LoadStoreLocationUseCase(
    private val repository: StoreLocationRepository,
    private val geocoder: AddressGeocoder,
) {
    suspend operator fun invoke(storeId: Long): StoreLocationResult {
        val result = repository.read(storeId)
        if (result !is StoreLocationResult.Success) return result
        val location = result.location
        val query = location.mapsQuery
        // Without stored coordinates, locate the address only to center the preview map.
        return if (location.hasCoordinates || !location.hasAddress || query == null) {
            result
        } else {
            StoreLocationResult.Success(location.withPreview(geocoder.locate(query)))
        }
    }
}
