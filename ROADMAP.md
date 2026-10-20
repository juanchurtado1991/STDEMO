# Roadmap STDEMO

App de ejemplo que lista las marcas del catálogo de SmartThings (paginadas y cacheadas en Room) y, al tocar una, muestra los dispositivos de esa marca.

Cada PR tiene que compilar y pasar sus tests por sí solo, y seguir las skills del proyecto:
- [`class-documentation`](.claude/skills/class-documentation/SKILL.md): KDoc en todas las clases.
- [`unit-tests`](.claude/skills/unit-tests/SKILL.md): tests unitarios en el mismo PR que la clase.

## Estado

| # | PR | Estado |
|---|---|---|
| 1 | build: dependencias | ✅ mergeado |
| 2 | feat(app): app con Hilt, MainActivity en Compose, tema | ✅ mergeado |
| 3 | docs: skill `class-documentation` | ✅ mergeado |
| 4 | test: skill `unit-tests` | ✅ mergeado |
| 5 | feat(domain): modelos, contrato `BrandRepository`, casos de uso | ✅ mergeado |
| 6 | docs: este roadmap | 🔄 en revisión |
| 7 | feat(data/remote): API Retrofit, DTOs, mappers, `NetworkModule` | ⏳ pendiente |
| 8 | test(data/remote): tests funcionales de los endpoints | ⏳ pendiente |
| 9 | feat(data/local): base de datos Room, entidades, DAOs | ⏳ pendiente |
| 10 | feat(data/paging): `BrandsRemoteMediator` con caché de 24 h | ⏳ pendiente |
| 11 | feat(data/paging): `DevicesPagingSource` (solo red) | ⏳ pendiente |
| 12 | feat(data): `BrandRepositoryImpl` + `RepositoryModule` | ⏳ pendiente |
| 13 | feat(ui): base MVI + componentes compartidos | ⏳ pendiente |
| 14 | feat(brands): pantalla de marcas + ViewModel | ⏳ pendiente |
| 15 | feat(devices): pantalla de dispositivos + ViewModel | ⏳ pendiente |
| 16 | feat(navigation): NavHost tipado Marcas → Dispositivos | ⏳ pendiente |
| 17 | docs: README | ⏳ pendiente |

## Notas sobre la API

Lo siguiente se verificó contra la API real el 2026-10-20.

- **Marcas:** `GET https://api.smartthings.com/catalogs/api/v3/brands?country=US&visibility=public&count=25&pagingState=<n>`
- **Dispositivos de una marca:** `GET https://api.smartthings.com/catalogs/api/v3/brands/{brandId}/devicetypes?count=25&pagingState=<n>`
- **Paginación:**
  - `count` fija el tamaño de página y `pagingState` es el número de página, empezando en `0`.
  - `currentPage`, `nextPage` y `totalPage` llegan como **strings**.
  - `totalPage` solo viene en la página 0, así que no sirve para saber cuándo parar.
  - Que **no** venga `nextPage` indica que es la última página.
- **Respuestas de dispositivos:** muchas marcas no tienen ninguno (`"products": []`), por eso el estado vacío es obligatorio.
- **Autenticación:** ninguno de los dos endpoints la necesita.

---

## PR 7: feat(data/remote): API Retrofit, DTOs, mappers, `NetworkModule`

**Objetivo:** tener la capa de red lista para consumir los dos endpoints.

- `data/remote/api/SmartThingsApi.kt`:
  - `getBrands(country = "US", visibility = "public", count, pagingState: String?)`
  - `getDeviceTypes(brandId, count, pagingState: String?)`
  - Las dos funciones son `suspend`.
- `data/remote/dto/`:
  - `BrandsResponseDto(currentPage, nextPage?, totalPage?, brands)`
  - `BrandDto(brandId, internalName, iconUrl?)`
  - `ProductsResponseDto(currentPage, nextPage?, totalPage?, products)`
  - `ProductDto(productId, modelName, modelCode?, protocol?)`
  - Todos con `@Serializable`; los campos de paginación son `String`.
- `data/mapper/`:
  - `BrandDto.toDomain()` y `ProductDto.toDomain()`.
  - Si `iconUrl` viene en blanco, se mapea a `null`.
- `data/remote/ErrorMapper.kt`: convierte `IOException`, `HttpException` y `SerializationException` en `DomainError`.
- `di/NetworkModule.kt`:
  - `Json { ignoreUnknownKeys = true; explicitNulls = false }`.
  - OkHttp con `HttpLoggingInterceptor` en nivel `BODY` solo cuando `BuildConfig.DEBUG`, y timeouts.
  - Retrofit con `BASE_URL` y el converter de kotlinx-serialization, más la instancia de `SmartThingsApi`.
- `di/DispatchersModule.kt`: un qualifier `@IoDispatcher`.

**Tests:**
- Mappers: todos los campos, nulos y strings en blanco.
- `ErrorMapper`: cada tipo de excepción da el `DomainError` correcto.

## PR 8: test(data/remote): tests funcionales de los endpoints

**Objetivo:** validar el contrato real con la API usando el stack completo de Retrofit y OkHttp.

- Fixtures en `src/test/resources/json/`, sacadas de respuestas reales:
  - `brands_page_0.json`, `brands_middle_page.json`, `brands_last_page.json`
  - `devicetypes_page_0.json`, `devicetypes_empty.json`
- `SmartThingsApiTest` con MockWebServer (`mockwebserver3`):
  - **Request:** path, `country`, `visibility`, `count` y `pagingState`. Sin `pagingState` en la primera página.
  - **Parseo:** primera página, página intermedia, última página sin `nextPage`, página sin `totalPage`, `products` vacío y campos desconocidos.
  - **Errores:** 404, 500 y JSON malformado se convierten en el `DomainError` esperado.
- `SmartThingsLiveApiTest`:
  - Pega a la API real y verifica la forma de la respuesta.
  - Se excluye por defecto y se activa con `-PliveApiTests`.

## PR 9: feat(data/local): base de datos Room, entidades, DAOs

**Objetivo:** guardar las marcas en una caché persistente.

- `data/local/entity/BrandEntity`:
  - Campos `brandId` (clave primaria), `name`, `iconUrl?`, `page` y `position`.
  - Índice sobre `(page, position)` para conservar el orden de la API.
- `data/local/entity/BrandRemoteKeyEntity`:
  - Una sola fila con `nextPage: String?` y `lastUpdated: Long`.
- `data/local/dao/BrandDao`:
  - `pagingSource()` ordenado por `page, position`.
  - `insertAll()` y `clearAll()`.
- `data/local/dao/RemoteKeyDao`: `upsert()`, `get()` y `clear()`.
- `data/local/StDemoDatabase.kt`:
  - Versión 1 con `exportSchema = true`; el esquema se guarda en `app/schemas/`.
- Mappers `BrandEntity.toDomain()` y `BrandDto.toEntity(page, position)`.
- `di/DatabaseModule.kt`: provee la base de datos y los DAOs.

**Tests (Robolectric + Room en memoria):**
- Orden por `page, position`.
- `clearAll`.
- Upsert de la remote key.
- Mappers de entidades.

## PR 10: feat(data/paging): `BrandsRemoteMediator` con caché de 24 h

**Objetivo:** paginación de red a Room con un estado de carga claro.

- `data/paging/BrandsRemoteMediator` (`@OptIn(ExperimentalPagingApi::class)`):
  - **REFRESH:** pide la página 0 y, en una sola transacción, `clearAll` y guarda las marcas con `page` y `position`, más la remote key.
  - **APPEND:** usa el `nextPage` guardado. Si es `null`, devuelve `endOfPaginationReached = true`.
  - **PREPEND:** devuelve el final de la paginación de inmediato.
  - **`initialize()`:** devuelve `SKIP_INITIAL_REFRESH` si `lastUpdated` tiene menos de 24 h y `LAUNCH_INITIAL_REFRESH` si es más viejo.
  - Errores: `MediatorResult.Error(DomainError)`.
- Se inyecta un `Clock` (o `TimeProvider`) para poder testear la lógica de las 24 h.

**Tests (Room en memoria + API fake o MockWebServer):**
- Primer refresh, carga de más páginas, final de la lista sin `nextPage`.
- Refresh que limpia la caché.
- Caché fresca contra caché vieja.
- Error de red.

## PR 11: feat(data/paging): `DevicesPagingSource` (solo red)

**Objetivo:** paginar los dispositivos de una marca, sin caché.

- `data/paging/DevicesPagingSource(api, brandId)`:
  - La clave es `String?`, donde `null` es la primera página.
  - `nextKey` sale de `nextPage`.
  - `getRefreshKey` devuelve `null`, así que el refresh empieza desde el principio.
  - Los errores se devuelven como `LoadResult.Error(DomainError)`.

**Tests (`TestPager`):**
- La primera carga devuelve los items y el `nextKey` correcto.
- La última página tiene `nextKey = null`.
- Lista vacía.
- Error.

## PR 12: feat(data): `BrandRepositoryImpl` + `RepositoryModule`

**Objetivo:** juntar la capa de datos detrás del contrato del dominio.

- `data/repository/BrandRepositoryImpl`:
  - `getBrands()`: un `Pager` con `PagingConfig(pageSize = 25)`, `remoteMediator = BrandsRemoteMediator` y `pagingSourceFactory = brandDao::pagingSource`, mapeado a dominio.
  - `getDevices(brandId)`: un `Pager` con `DevicesPagingSource`.
- `di/RepositoryModule`: `@Binds BrandRepository`.

**Tests:**
- `asSnapshot()` sobre los dos flujos, con Room en memoria y MockWebServer.

## PR 13: feat(ui): base MVI + componentes compartidos

**Objetivo:** tener una base común para las pantallas.

- `presentation/mvi/`: las interfaces `UiState`, `UiIntent` y `UiEffect`, y opcionalmente un `MviViewModel<S, I, E>` base con:
  - `state: StateFlow<S>`.
  - `effects: Flow<E>`, implementado con un `Channel`.
  - `onIntent(intent)`.
- `presentation/components/`:
  - `LoadingIndicator`, `ErrorState(message, onRetry)` y `EmptyState(message)`.
  - `PagingFooter(loadState, onRetry)`.
  - `RemoteImage` (Glide con placeholder e imagen de error).
- `DomainError.toUserMessage()`: convierte cada error en un string resource.
- Strings en `res/values/strings.xml`.

**Tests:**
- El ViewModel base: emisión de estado y de efectos con Turbine.
- `toUserMessage()`.

## PR 14: feat(brands): pantalla de marcas + ViewModel

**Objetivo:** la primera pantalla funcionando.

- `presentation/brands/BrandsContract`:
  - **State:** `isRefreshing` y `error: DomainError?`.
  - **Intent:** `Refresh`, `Retry`, `BrandClicked(brand)` y `LoadStateChanged(loadStates)`.
  - **Effect:** `NavigateToDevices(brandId, brandName)`.
- `BrandsViewModel` (`@HiltViewModel`):
  - Expone `brands: Flow<PagingData<Brand>>` con `cachedIn(viewModelScope)` y procesa los intents.
- `BrandsScreen`:
  - Un `LazyColumn` con `collectAsLazyPagingItems()`; cada fila muestra el ícono y el nombre de la marca.
  - Pull-to-refresh y footer de paginación.
  - Estados de carga, error y vacío.
  - Banner de "sin conexión" cuando hay caché pero falla la red.

**Tests:**
- Estado inicial.
- `Refresh` y `Retry`.
- `BrandClicked` emite `NavigateToDevices` una sola vez.
- Los errores se reflejan en el estado.
- `brands.asSnapshot()` devuelve los datos del fake.

## PR 15: feat(devices): pantalla de dispositivos + ViewModel

**Objetivo:** la segunda pantalla.

- `presentation/devices/DevicesContract`:
  - **State:** `brandName`, `isRefreshing` y `error`.
  - **Intent:** `Retry`, `Back` y `LoadStateChanged`.
  - **Effect:** `NavigateBack`.
- `DevicesViewModel`: lee `brandId` y `brandName` del `SavedStateHandle` con `toRoute<DevicesRoute>()`.
- `DevicesScreen`:
  - `TopAppBar` con el nombre de la marca y la flecha de volver.
  - Lista paginada: `modelName` como título, `modelCode` como subtítulo y un chip con el `protocol`.
  - Estado vacío "Esta marca no tiene dispositivos".

**Tests:**
- Lectura de argumentos.
- Estado inicial.
- Error y retry.
- Lista vacía.
- `Back` emite `NavigateBack`.

## PR 16: feat(navigation): NavHost tipado Marcas → Dispositivos

**Objetivo:** conectar las dos pantallas.

- `presentation/navigation/Routes.kt`: `@Serializable object BrandsRoute` y `@Serializable data class DevicesRoute(brandId, brandName)`.
- `AppNavHost`:
  - `composable<BrandsRoute>` y `composable<DevicesRoute>`.
  - Los efectos se recolectan en un `LaunchedEffect` y llaman a `navController.navigate` o `popBackStack`.
- `MainActivity`: el placeholder se reemplaza por `AppNavHost`.

**Tests:**
- Navegación con `TestNavHostController` (Robolectric + Compose UI test): tocar una marca lleva a Dispositivos y volver regresa a la lista.

## PR 17: docs: README

**Objetivo:** documentar el proyecto.

- Arquitectura (capas, MVI y flujo de datos con un diagrama).
- Stack tecnológico y versiones.
- Notas sobre la paginación de la API (`pagingState`, `nextPage`, `totalPage`).
- Política de caché (24 h y pull-to-refresh).
- Cómo correr la app, los tests unitarios y los tests contra la API real (`-PliveApiTests`).
- Workaround para el daemon de Kotlin de Android Studio Flatpak: `-Pkotlin.compiler.execution.strategy=in-process`.
