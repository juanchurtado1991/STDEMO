package com.example.stdemo.testutil

import androidx.paging.PagingData
import com.example.stdemo.domain.model.Brand
import com.example.stdemo.domain.model.Device
import com.example.stdemo.domain.repository.BrandRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** In-memory [BrandRepository] that serves fixed data and records requested brand ids. */
class FakeBrandRepository(
    var brands: List<Brand> = emptyList(),
    var devicesByBrand: Map<String, List<Device>> = emptyMap(),
) : BrandRepository {

    val requestedBrandIds = mutableListOf<String>()

    override fun getBrands(): Flow<PagingData<Brand>> = flowOf(PagingData.from(brands))

    override fun getDevices(brandId: String): Flow<PagingData<Device>> {
        requestedBrandIds += brandId
        return flowOf(PagingData.from(devicesByBrand[brandId].orEmpty()))
    }
}
