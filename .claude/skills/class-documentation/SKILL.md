---
name: class-documentation
description: Write or update KDoc for Kotlin classes, interfaces, objects and public functions in this project. Use whenever a new class is created or an existing class's public API changes, and when asked to "document", "add KDoc" or "add class docs".
---

# Class documentation (KDoc)

Every new or changed Kotlin type in `app/src/main` gets KDoc that follows the rules below.
Test classes get a one-line class KDoc only (what they cover).

## General rules

- Use KDoc (`/** ... */`), never `//` comments, for types and public members.
- The first sentence is a summary in the third person ("Fetches...", "Represents..."), ends with a period, and fits on one line where possible.
- Describe **what** the class is responsible for and **why** it exists; don't restate the class name or its signature.
- Reference other symbols with `[ClassName]` / `[ClassName.member]` links instead of plain text.
- Document every constructor parameter with `@param` (or `@property` for `val`/`var` properties) unless the name is completely self-explanatory.
- Document thrown exceptions with `@throws` and non-obvious return values with `@return`.
- Mention threading/dispatcher expectations and side effects (network, disk, database) when they exist.
- Keep docs in English, wrap at ~100 columns, and keep them in sync when the code changes.
- Don't document `private` members unless the logic is non-obvious.
- Don't document `override` members unless the override changes the contract.

## Per-layer templates

### Domain model

```kotlin
/**
 * A device manufacturer listed in the SmartThings catalog.
 *
 * @property id Unique SmartThings brand identifier.
 * @property name Human-readable brand name shown in the UI.
 * @property iconUrl Remote URL of the brand logo, or `null` when the catalog has none.
 */
@Parcelize
data class Brand(...)
```

### Repository interface (domain)

```kotlin
/**
 * Single source of truth for brand and device catalog data.
 *
 * Implementations decide where data comes from (network, cache), so callers
 * depend only on this contract.
 */
interface BrandRepository
```

### Use case

```kotlin
/**
 * Returns a paged stream of brands available in the US catalog.
 *
 * @param repository Source of brand data.
 */
class GetBrandsUseCase @Inject constructor(...) {
    /** Runs the use case; collection is cold and safe to restart. */
    operator fun invoke(): Flow<PagingData<Brand>>
}
```

### DTO / Room entity

```kotlin
/**
 * Network representation of a brand returned by `GET catalogs/api/v3/brands`.
 *
 * Mapped to the domain [Brand] by [toDomain].
 */
@Serializable
data class BrandDto(...)
```

### Repository / data source implementation

State the source of the data, caching policy and error mapping:

```kotlin
/**
 * [BrandRepository] backed by the SmartThings catalog API with a Room cache.
 *
 * Brands are paged through [BrandsRemoteMediator]; devices are loaded from the
 * network only. Network failures are surfaced as [DomainError].
 */
```

### MVI contract

Document the contract object and each State field, Intent and Effect:

```kotlin
/** MVI contract for the brands screen. */
object BrandsContract {
    /**
     * Render state for the brands screen.
     *
     * @property isRefreshing `true` while a full refresh is in progress.
     */
    data class State(...)

    /** User actions handled by [BrandsViewModel]. */
    sealed interface Intent {
        /** Reloads the list from the first page, clearing the cache. */
        data object Refresh : Intent
    }

    /** One-off events the UI consumes once (navigation, snackbars). */
    sealed interface Effect
}
```

### ViewModel

```kotlin
/**
 * Holds [BrandsContract.State] for the brands screen and processes
 * [BrandsContract.Intent]s.
 *
 * Emits [BrandsContract.Effect.NavigateToDevices] when a brand is selected.
 */
@HiltViewModel
class BrandsViewModel
```

### Composable screen / component

```kotlin
/**
 * Paged list of brands with pull-to-refresh.
 *
 * @param state Current render state.
 * @param onIntent Callback for user actions.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun BrandsScreen(...)
```

### Hilt module

```kotlin
/** Provides the OkHttp client, Retrofit instance and [SmartThingsApi]. */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule
```

## Checklist before finishing

1. Every new public/internal type has a class KDoc.
2. Constructor params/properties are documented where not obvious.
3. `[links]` resolve to real symbols.
4. Existing docs still match behaviour after the change.
