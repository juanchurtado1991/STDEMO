---
name: unit-tests
description: Generate or update JVM unit tests for every Kotlin class Claude creates or modifies in this project (use cases, mappers, repositories, RemoteMediator/PagingSource, DAOs, ViewModels, Retrofit API). Use whenever AI-generated production code is added or changed, and when asked to "add tests", "write unit tests" or "cover this class".
---

# Unit tests for AI-generated classes

**Rule:** any production class Claude creates or changes in `app/src/main` must ship with
unit tests in the **same commit/PR**. A PR without matching tests is incomplete.

## Scope

| Must test | Skip |
|---|---|
| Use cases, mappers, repositories | Hilt `@Module`s (covered indirectly) |
| `RemoteMediator`, `PagingSource`, DAOs | Theme / color / typography files |
| ViewModels (State, Intent handling, Effects) | `Application`, `MainActivity` |
| Retrofit API (functional, via MockWebServer) | Pure data classes with no logic |
| Composables with logic (optional, Robolectric + Compose test) | Generated code (Hilt, Room, KSP) |

## Location and naming

- Mirror the production package under `app/src/test/java/...`.
- One test class per production class: `<ClassName>Test.kt`.
- Test names use backticks and describe behaviour:
  `` `refresh with empty cache loads first page and stores next key`() ``.
- Structure each test as **given / when / then**. Use blank lines or comments between sections.
- Give each test class the one-line KDoc required by the `class-documentation` skill.

## Tooling (already in `libs.versions.toml`)

- **JUnit4**: `@Test`, `@Before`, `@get:Rule`.
- **kotlinx-coroutines-test**: `runTest`, `StandardTestDispatcher`, `advanceUntilIdle`.
- **Turbine**: `flow.test { awaitItem() ... }` for StateFlow/SharedFlow/Channel.
- **MockK**: `mockk`, `coEvery`, `coVerify`. Prefer **hand-written fakes** for repositories and DAOs. Use MockK only for collaborators that are hard to fake.
- **paging-testing**: `flow.asSnapshot()` and `TestPager` for `PagingSource`.
- **Robolectric** + **room-testing**: in-memory Room (`Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries()`).
- **MockWebServer** (`mockwebserver3`): real Retrofit/OkHttp stack against canned JSON.

## Shared test utilities

Create these once under `app/src/test/java/com/example/stdemo/testutil/` and reuse them:

```kotlin
/** Replaces Dispatchers.Main with a test dispatcher for ViewModel tests. */
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
```

- `TestData.kt`: builders such as `aBrand(id = "1", name = "Acme")` with defaults.
- `src/test/resources/json/`: API fixtures (`brands_page_0.json`, `brands_last_page.json`,
  `devicetypes_empty.json`, …) captured from the real API. Load them with a `readResource(name)` helper.

## Per-layer recipes

### Use case
- Calls the repository with the right arguments and returns its result unchanged (or transformed).
- Use a fake repository. Assert on the output, not on internal calls.

### Mapper
- Maps every field. Null or missing optional fields fall back to the documented defaults.
- Edge cases: blank strings, empty lists, unknown enum values.

### Retrofit API (functional)
- Enqueue fixture responses with `MockResponse`, then call the real API interface.
- Assert the **request**: path, `country`, `visibility`, `count` and `pagingState` query params.
- Assert **parsing**: first page, last page (no `nextPage`), page without `totalPage`, empty `products`, unknown fields.
- Assert **errors**: 4xx, 5xx and malformed JSON map to the expected exception or `DomainError`.

### DAO (Robolectric + in-memory Room)
- Insert/query order (`page, position`), `clearAll`, remote-key upsert.
- Close the database in `@After`.

### RemoteMediator
- Use in-memory Room plus MockWebServer (or a fake API).
- REFRESH: clears the cache, stores page 0 and the next key.
- APPEND: uses the stored `nextPage`. Returns `endOfPaginationReached = true` when `nextPage` is absent.
- PREPEND: returns end reached immediately.
- `initialize()`: `SKIP_INITIAL_REFRESH` if the cache is under 24h old, otherwise `LAUNCH_INITIAL_REFRESH`. Inject a clock so this is testable.
- A network error returns `MediatorResult.Error`.

### PagingSource
- Use `TestPager`: the first load returns items and the correct `nextKey`. The last page has `nextKey = null`. An error returns `LoadResult.Error`.

### ViewModel (MVI)
- Use `@get:Rule val mainDispatcherRule = MainDispatcherRule()` and a fake use case or repository.
- Test the **initial state**.
- For each **Intent**: the resulting State sequence (use Turbine on `state`).
- For each **Effect**: emitted once with the correct payload (Turbine on `effects`).
- Error → retry flow. Read `SavedStateHandle` args (Devices screen).
- Paged data: `viewModel.brands.asSnapshot()` returns the fake items.

## Example

```kotlin
/** Unit tests for [GetBrandsUseCase]. */
class GetBrandsUseCaseTest {

    private val repository = FakeBrandRepository()
    private val useCase = GetBrandsUseCase(repository)

    @Test
    fun `invoke returns brands from repository`() = runTest {
        // given
        repository.brands = listOf(aBrand(id = "1"), aBrand(id = "2"))

        // when
        val result = useCase().asSnapshot()

        // then
        assertEquals(listOf("1", "2"), result.map { it.id })
    }
}
```

## Running

```bash
./gradlew testDebugUnitTest -Pkotlin.compiler.execution.strategy=in-process
```

The `-P` flag avoids the Android Studio Flatpak Kotlin daemon JDK issue on this machine.

## Checklist before finishing

1. Every new or changed production class in scope has a matching `*Test.kt`.
2. Each test covers the success path, the error path and at least one edge case.
3. No real network calls in unit tests. Live API tests are tagged and excluded by default.
4. Tests are deterministic: no `Thread.sleep`, no real clock, no real `Dispatchers.IO`.
5. `./gradlew testDebugUnitTest` passes locally before committing.
