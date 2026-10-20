package com.example.stdemo.domain.usecase

import androidx.paging.PagingData
import com.example.stdemo.domain.model.Device
import com.example.stdemo.domain.repository.BrandRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Returns the paged list of device types published by a single brand.
 *
 * @param repository Source of device data.
 */
class GetDevicesByBrandUseCase @Inject constructor(
    private val repository: BrandRepository,
) {
    /**
     * Runs the use case; the returned flow is cold and safe to collect again.
     *
     * @param brandId Identifier of the brand whose devices are requested.
     * @throws IllegalArgumentException if [brandId] is blank.
     */
    operator fun invoke(brandId: String): Flow<PagingData<Device>> {
        require(brandId.isNotBlank()) { "brandId must not be blank" }
        return repository.getDevices(brandId)
    }
}
