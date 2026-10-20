# STDEMO Roadmap

A sample app that lists the brands in the SmartThings catalog (paged and cached in Room). Tapping a brand shows that brand's devices.

Each PR must build and pass its tests on its own. Every PR also follows the project skills:
- [`class-documentation`](.claude/skills/class-documentation/SKILL.md): KDoc on every class.
- [`unit-tests`](.claude/skills/unit-tests/SKILL.md): unit tests in the same PR as the class.

## Status

| # | PR | Status |
|---|---|---|
| 1 | build: dependencies | ✅ merged |
| 2 | feat(app): Hilt app, Compose MainActivity, theme | ✅ merged |
| 3 | docs: `class-documentation` skill | ✅ merged |
| 4 | test: `unit-tests` skill | ✅ merged |
| 5 | feat(domain): models, `BrandRepository` contract, use cases | ✅ merged |
| 6 | docs: this roadmap | 🔄 in review |
| 7 | feat(data/remote): Retrofit API, DTOs, mappers, `NetworkModule` | ⏳ pending |
| 8 | test(data/remote): functional endpoint tests | ⏳ pending |
| 9 | feat(data/local): Room database, entities, DAOs | ⏳ pending |
| 10 | feat(data/paging): `BrandsRemoteMediator` with 24h cache check | ⏳ pending |
| 11 | feat(data/paging): `DevicesPagingSource` (network only) | ⏳ pending |
| 12 | feat(data): `BrandRepositoryImpl` + `RepositoryModule` | ⏳ pending |
| 13 | feat(ui): MVI base + shared components | ⏳ pending |
| 14 | feat(brands): Brands screen + ViewModel | ⏳ pending |
| 15 | feat(devices): Devices screen + ViewModel | ⏳ pending |
| 16 | feat(navigation): type-safe NavHost Brands → Devices | ⏳ pending |
| 17 | docs: README | ⏳ pending |

## API notes

The notes below were verified against the live API on 2026-10-20.

- **Brands:** `GET https://api.smartthings.com/catalogs/api/v3/brands?country=US&visibility=public&count=25&pagingState=<n>`
- **Devices of a brand:** `GET https://api.smartthings.com/catalogs/api/v3/brands/{brandId}/devicetypes?count=25&pagingState=<n>`
- **Pagination:**
  - `count` sets the page size, and `pagingState` is the page number, starting at `0`.
  - `currentPage`, `nextPage` and `totalPage` are returned as **strings**.
  - `totalPage` only appears on page 0, so it can't be used to decide when to stop.
  - A missing `nextPage` marks the last page.
- **Device responses:** many brands have no devices (`"products": []`), so the empty state is required.
- **Authentication:** neither endpoint requires it.

---

## PR 7: feat(data/remote): Retrofit API, DTOs, mappers, `NetworkModule`

**Goal:** a network layer ready to call both endpoints.

- `data/remote/api/SmartThingsApi.kt`:
  - `getBrands(country = "US", visibility = "public", count, pagingState: String?)`
  - `getDeviceTypes(brandId, count, pagingState: String?)`
  - Both functions are `suspend`.
- `data/remote/dto/`:
  - `BrandsResponseDto(currentPage, nextPage?, totalPage?, brands)`
  - `BrandDto(brandId, internalName, iconUrl?)`
  - `ProductsResponseDto(currentPage, nextPage?, totalPage?, products)`
  - `ProductDto(productId, modelName, modelCode?, protocol?)`
  - All are `@Serializable`, and the paging fields are `String`.
- `data/mapper/`:
  - `BrandDto.toDomain()` and `ProductDto.toDomain()`.
  - A blank `iconUrl` maps to `null`.
- `data/remote/ErrorMapper.kt`: maps `IOException`, `HttpException` and `SerializationException` to `DomainError`.
- `di/NetworkModule.kt`:
  - `Json { ignoreUnknownKeys = true; explicitNulls = false }`.
  - OkHttp with `HttpLoggingInterceptor` at `BODY` level only when `BuildConfig.DEBUG`, plus timeouts.
  - Retrofit with `BASE_URL` and the kotlinx-serialization converter, plus the `SmartThingsApi` instance.
- `di/DispatchersModule.kt`: an `@IoDispatcher` qualifier.

**Tests:**
- Mappers: every field, nulls and blank strings.
- `ErrorMapper`: each exception type gives the right `DomainError`.

## PR 8: test(data/remote): functional endpoint tests

**Goal:** check the real API contract through the full Retrofit and OkHttp stack.

- Fixtures in `src/test/resources/json/`, captured from real responses:
  - `brands_page_0.json`, `brands_middle_page.json`, `brands_last_page.json`
  - `devicetypes_page_0.json`, `devicetypes_empty.json`
- `SmartThingsApiTest` with MockWebServer (`mockwebserver3`):
  - **Request:** path, `country`, `visibility`, `count` and `pagingState`. No `pagingState` on the first page.
  - **Parsing:** first page, middle page, last page without `nextPage`, page without `totalPage`, empty `products` and unknown fields.
  - **Errors:** 404, 500 and malformed JSON map to the expected `DomainError`.
- `SmartThingsLiveApiTest`:
  - Calls the real API and checks the shape of the response.
  - It's excluded by default and enabled with `-PliveApiTests`.

## PR 9: feat(data/local): Room database, entities, DAOs

**Goal:** a persistent cache for brands.

- `data/local/entity/BrandEntity`:
  - Fields `brandId` (primary key), `name`, `iconUrl?`, `page` and `position`.
  - An index on `(page, position)` keeps the API order.
- `data/local/entity/BrandRemoteKeyEntity`:
  - A single row with `nextPage: String?` and `lastUpdated: Long`.
- `data/local/dao/BrandDao`:
  - `pagingSource()` ordered by `page, position`.
  - `insertAll()` and `clearAll()`.
- `data/local/dao/RemoteKeyDao`: `upsert()`, `get()` and `clear()`.
- `data/local/StDemoDatabase.kt`:
  - Version 1 with `exportSchema = true`; the schema is saved in `app/schemas/`.
- Mappers `BrandEntity.toDomain()` and `BrandDto.toEntity(page, position)`.
- `di/DatabaseModule.kt`: provides the database and the DAOs.

**Tests (Robolectric + in-memory Room):**
- Ordering by `page, position`.
- `clearAll`.
- Remote key upsert.
- Entity mappers.

## PR 10: feat(data/paging): `BrandsRemoteMediator` with 24h cache check

**Goal:** network-to-Room paging with clear load states.

- `data/paging/BrandsRemoteMediator` (`@OptIn(ExperimentalPagingApi::class)`):
  - **REFRESH:** fetches page 0, then in one transaction runs `clearAll` and saves the brands (with `page` and `position`) and the remote key.
  - **APPEND:** uses the stored `nextPage`. If it's `null`, returns `endOfPaginationReached = true`.
  - **PREPEND:** returns end of pagination immediately.
  - **`initialize()`:** returns `SKIP_INITIAL_REFRESH` if `lastUpdated` is less than 24h old, otherwise `LAUNCH_INITIAL_REFRESH`.
  - Errors: `MediatorResult.Error(DomainError)`.
- A `Clock` (or `TimeProvider`) is injected so the 24h logic can be tested.

**Tests (in-memory Room + fake API or MockWebServer):**
- First refresh, loading more pages, end of the list when `nextPage` is missing.
- Refresh clearing the cache.
- Fresh cache versus stale cache.
- Network error.

## PR 11: feat(data/paging): `DevicesPagingSource` (network only)

**Goal:** page a brand's devices, without caching.

- `data/paging/DevicesPagingSource(api, brandId)`:
  - The key is `String?`, where `null` means the first page.
  - `nextKey` comes from `nextPage`.
  - `getRefreshKey` returns `null`, so a refresh starts from the beginning.
  - Errors are returned as `LoadResult.Error(DomainError)`.

**Tests (`TestPager`):**
- The first load returns the items and the right `nextKey`.
- The last page has `nextKey = null`.
- Empty list.
- Error.

## PR 12: feat(data): `BrandRepositoryImpl` + `RepositoryModule`

**Goal:** wire the data layer behind the domain contract.

- `data/repository/BrandRepositoryImpl`:
  - `getBrands()`: a `Pager` with `PagingConfig(pageSize = 25)`, `remoteMediator = BrandsRemoteMediator` and `pagingSourceFactory = brandDao::pagingSource`, mapped to domain models.
  - `getDevices(brandId)`: a `Pager` with `DevicesPagingSource`.
- `di/RepositoryModule`: `@Binds BrandRepository`.

**Tests:**
- `asSnapshot()` on both flows, with in-memory Room and MockWebServer.

## PR 13: feat(ui): MVI base + shared components

**Goal:** a shared foundation for the screens.

- `presentation/mvi/`: the `UiState`, `UiIntent` and `UiEffect` interfaces, and optionally a base `MviViewModel<S, I, E>` with:
  - `state: StateFlow<S>`.
  - `effects: Flow<E>`, backed by a `Channel`.
  - `onIntent(intent)`.
- `presentation/components/`:
  - `LoadingIndicator`, `ErrorState(message, onRetry)` and `EmptyState(message)`.
  - `PagingFooter(loadState, onRetry)`.
  - `RemoteImage` (Glide with a placeholder and an error image).
- `DomainError.toUserMessage()`: maps each error to a string resource.
- Strings in `res/values/strings.xml`.

**Tests:**
- The base ViewModel: state and effect emission with Turbine.
- `toUserMessage()`.

## PR 14: feat(brands): Brands screen + ViewModel

**Goal:** the first working screen.

- `presentation/brands/BrandsContract`:
  - **State:** `isRefreshing` and `error: DomainError?`.
  - **Intent:** `Refresh`, `Retry`, `BrandClicked(brand)` and `LoadStateChanged(loadStates)`.
  - **Effect:** `NavigateToDevices(brandId, brandName)`.
- `BrandsViewModel` (`@HiltViewModel`):
  - Exposes `brands: Flow<PagingData<Brand>>` with `cachedIn(viewModelScope)` and handles the intents.
- `BrandsScreen`:
  - A `LazyColumn` using `collectAsLazyPagingItems()`; each row shows the brand icon and name.
  - Pull-to-refresh and a paging footer.
  - Loading, error and empty states.
  - An offline banner when cached data is shown but the network fails.

**Tests:**
- Initial state.
- `Refresh` and `Retry`.
- `BrandClicked` emits `NavigateToDevices` exactly once.
- Errors show up in the state.
- `brands.asSnapshot()` returns the fake data.

## PR 15: feat(devices): Devices screen + ViewModel

**Goal:** the second screen.

- `presentation/devices/DevicesContract`:
  - **State:** `brandName`, `isRefreshing` and `error`.
  - **Intent:** `Retry`, `Back` and `LoadStateChanged`.
  - **Effect:** `NavigateBack`.
- `DevicesViewModel`: reads `brandId` and `brandName` from `SavedStateHandle` with `toRoute<DevicesRoute>()`.
- `DevicesScreen`:
  - A `TopAppBar` with the brand name and a back arrow.
  - A paged list with `modelName` as the title, `modelCode` as the subtitle and a `protocol` chip.
  - An empty state: "This brand has no devices".

**Tests:**
- Reading the arguments.
- Initial state.
- Error and retry.
- Empty list.
- `Back` emits `NavigateBack`.

## PR 16: feat(navigation): type-safe NavHost Brands → Devices

**Goal:** connect the two screens.

- `presentation/navigation/Routes.kt`: `@Serializable object BrandsRoute` and `@Serializable data class DevicesRoute(brandId, brandName)`.
- `AppNavHost`:
  - `composable<BrandsRoute>` and `composable<DevicesRoute>`.
  - Effects are collected in a `LaunchedEffect` and call `navController.navigate` or `popBackStack`.
- `MainActivity`: the placeholder is replaced with `AppNavHost`.

**Tests:**
- Navigation with `TestNavHostController` (Robolectric + Compose UI test): tapping a brand opens Devices, and going back returns to the list.

## PR 17: docs: README

**Goal:** document the project.

- Architecture (layers, MVI and data flow, with a diagram).
- Tech stack and versions.
- Notes on API pagination (`pagingState`, `nextPage`, `totalPage`).
- Cache policy (24h and pull-to-refresh).
- How to run the app, the unit tests and the live API tests (`-PliveApiTests`).
- Workaround for the Android Studio Flatpak Kotlin daemon: `-Pkotlin.compiler.execution.strategy=in-process`.
