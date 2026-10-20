package com.example.stdemo.domain.usecase

import androidx.paging.testing.asSnapshot
import com.example.stdemo.testutil.FakeBrandRepository
import com.example.stdemo.testutil.aDevice
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [GetDevicesByBrandUseCase]. */
class GetDevicesByBrandUseCaseTest {

    private val repository = FakeBrandRepository()
    private val useCase = GetDevicesByBrandUseCase(repository)

    @Test
    fun `invoke returns devices of the requested brand`() = runTest {
        // given
        repository.devicesByBrand = mapOf(
            "brand-1" to listOf(aDevice(id = "d1"), aDevice(id = "d2")),
            "brand-2" to listOf(aDevice(id = "d3")),
        )

        // when
        val result = useCase("brand-1").asSnapshot()

        // then
        assertEquals(listOf("d1", "d2"), result.map { it.id })
        assertEquals(listOf("brand-1"), repository.requestedBrandIds)
    }

    @Test
    fun `invoke returns empty list when brand has no devices`() = runTest {
        // given
        repository.devicesByBrand = emptyMap()

        // when
        val result = useCase("brand-without-devices").asSnapshot()

        // then
        assertTrue(result.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invoke with blank brand id throws`() {
        // when
        useCase("  ")
    }

    @Test
    fun `invoke with blank brand id does not hit repository`() {
        // when
        runCatching { useCase("") }

        // then
        assertTrue(repository.requestedBrandIds.isEmpty())
    }
}
