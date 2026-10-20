package com.example.stdemo.domain.usecase

import androidx.paging.testing.asSnapshot
import com.example.stdemo.testutil.FakeBrandRepository
import com.example.stdemo.testutil.aBrand
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [GetBrandsUseCase]. */
class GetBrandsUseCaseTest {

    private val repository = FakeBrandRepository()
    private val useCase = GetBrandsUseCase(repository)

    @Test
    fun `invoke returns brands from repository in order`() = runTest {
        // given
        repository.brands = listOf(aBrand(id = "1", name = "Zeta"), aBrand(id = "2", name = "Alpha"))

        // when
        val result = useCase().asSnapshot()

        // then
        assertEquals(listOf("1", "2"), result.map { it.id })
    }

    @Test
    fun `invoke returns empty list when repository has no brands`() = runTest {
        // given
        repository.brands = emptyList()

        // when
        val result = useCase().asSnapshot()

        // then
        assertTrue(result.isEmpty())
    }
}
