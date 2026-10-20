package com.example.stdemo.domain.usecase

import androidx.paging.PagingData
import com.example.stdemo.domain.model.Brand
import com.example.stdemo.domain.repository.BrandRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Returns the paged list of brands available in the catalog.
 *
 * @param repository Source of brand data.
 */
class GetBrandsUseCase @Inject constructor(
    private val repository: BrandRepository,
) {
    /** Runs the use case; the returned flow is cold and safe to collect again. */
    operator fun invoke(): Flow<PagingData<Brand>> = repository.getBrands()
}
