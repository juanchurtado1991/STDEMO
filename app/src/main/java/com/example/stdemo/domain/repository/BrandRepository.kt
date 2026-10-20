package com.example.stdemo.domain.repository

import androidx.paging.PagingData
import com.example.stdemo.domain.model.Brand
import com.example.stdemo.domain.model.Device
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for SmartThings brand and device catalog data.
 *
 * Implementations decide where data comes from (network, cache), so callers depend only on
 * this contract. Load failures are reported as [com.example.stdemo.domain.model.DomainError]
 * inside the paging load states.
 */
interface BrandRepository {

    /** Returns a cold, paged stream of all public brands in the US catalog. */
    fun getBrands(): Flow<PagingData<Brand>>

    /**
     * Returns a cold, paged stream of the device types published by a brand.
     *
     * @param brandId Identifier of the brand, as in [Brand.id].
     */
    fun getDevices(brandId: String): Flow<PagingData<Device>>
}
