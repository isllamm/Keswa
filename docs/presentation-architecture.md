# Keswa — Clean Architecture & MVI

How the layers are drawn, and the exact presentation contract every screen follows.
Companion to [architecture.md](architecture.md) (modules, dependency rules, platform concerns).

---

## 1. Clean Architecture, concretely

Dependencies point **inward only**. The domain layer knows nothing about anything else.

```
┌─────────────────────────────────────────────────────────────┐
│  PRESENTATION            :feature:*   :core:ui   :app:*      │
│  Compose screens · Store (MVI) · Contract · UI formatting    │
│                              │ depends on ↓                  │
├─────────────────────────────────────────────────────────────┤
│  DOMAIN                  :domain   :core:common              │
│  Entities · value objects · use cases · policy               │
│  Repository INTERFACES · Principal · typed errors            │
│  ▲ depends on NOTHING (Kotlin stdlib + kotlinx only)         │
├─────────────────────────────────────────────────────────────┤
│  DATA                    :data   :sync:*                     │
│  Repository IMPLEMENTATIONS · SQLDelight · mappers           │
│  UnitOfWork · ReportEngine impl · (later) HTTP sources       │
│                              │ implements ↑                  │
└─────────────────────────────────────────────────────────────┘
                  :app:desktop wires the three together
```

| Layer | Owns | Depends on | Must never know about |
|---|---|---|---|
| Presentation | UI state, intents, effects, navigation, formatting | Domain | SQL, SQLDelight types, HTTP, POI, `java.*` |
| Domain | Business rules, entities, use cases, repository **interfaces** | Nothing | Compose, persistence, platform, frameworks |
| Data | Repository **implementations**, persistence, mapping, sync | Domain | Compose, UI state, navigation |

**Dependency inversion in practice:** `:domain` declares `interface SaleRepository`. `:data` implements it.
`:feature:pos` uses the interface and never sees the implementation — `:app:desktop` binds them. This is
what makes local↔remote data sources swappable and is enforced by the Gradle dependency-rules plugin
([ADR-005](adr/ADR-005-module-graph-and-dependency-rules.md)).

### 1.1 Models per layer — and where NOT to add a layer

| Boundary | Rule |
|---|---|
| DB ↔ Domain | **Map.** SQLDelight generates its own row types; they stay inside `:data` and are mapped to domain models. Non-negotiable — leaking generated types into `:domain` breaks the whole scheme. |
| Wire ↔ Domain | **Map.** `:sync:contract` DTOs are mapped in `:data`. Protocol changes must not touch domain models. |
| Domain ↔ UI state | **Usually don't map.** Put domain models straight into `State` and format at render time with `:core:ui` formatters. Introduce a UI model *only* when a screen needs computed/denormalized fields (e.g. `CartLineUi` carrying a pre-computed line total and a display label). |

> **ASSUMPTION:** the third mapping layer is opt-in, not mandatory. Strict Clean Architecture would
> demand a UI model for every screen. At 10h/week that is hundreds of pass-through files and mappers
> that only ever copy fields. If you want the strict version, say so — it is a consistent choice, just
> a more expensive one.

### 1.2 Use cases — granularity

- **Every mutation goes through a use case.** `CompleteSale`, `PostStockCount`, `ReceiveGoods`,
  `RecordCustomerPayment`, `CloseShift`. These carry the business rules, take a `Principal`, and are
  where the domain tests live.
- **Reads with rules go through a use case.** `ResolvePrice`, `ValidateReturn`, `ComputeShiftExpectedCash`.
- **Plain observational reads may call the repository interface directly** from the Store —
  `productRepository.observeAll(filter)`. A use case that only forwards one call adds a file, a test
  double and no rule.

One class, one operation, `operator fun invoke`:

```kotlin
class CompleteSale(
  private val sales: SaleRepository,
  private val stock: StockRepository,
  private val pricing: ResolvePrice,
  private val clock: Clock,
) {
  suspend operator fun invoke(principal: Principal, draft: SaleDraft): AppResult<Sale> { … }
}
```

**ASSUMPTION:** pragmatic granularity as above. Flip to "every read is a use case too" if you prefer
the stricter reading — it's a one-line change to this doc and a lot of extra files.

---

## 2. MVI — the contract

Every screen has exactly one `Contract` file declaring three types, and one `Store`.

```kotlin
// :feature:pos/PosContract.kt
object PosContract {

  data class State(
    val isLoading: Boolean = false,
    val cart: List<CartLine> = emptyList(),
    val totals: SaleTotals = SaleTotals.EMPTY,
    val customer: Customer? = null,
    val channel: ChannelCode = ChannelCode.RETAIL,
    val searchQuery: String = "",
    val searchResults: List<ProductVariant> = emptyList(),
    val activeDialog: Dialog? = null,
    val error: ErrorKey? = null,          // i18n key, never a message string
  ) : MviState

  sealed interface Intent : MviIntent {
    // from the user
    data class SearchChanged(val query: String) : Intent
    data class VariantScanned(val barcode: String) : Intent
    data class AddLine(val variantId: String) : Intent
    data class ChangeQty(val lineId: String, val qty: Int) : Intent
    data object CompleteSaleClicked : Intent
    data object ErrorDismissed : Intent

    // produced by the store itself — never constructed by a composable
    sealed interface Internal : Intent {
      data class SearchLoaded(val results: List<ProductVariant>) : Internal
      data class SaleCompleted(val sale: Sale) : Internal
      data class Failed(val error: ErrorKey) : Internal
    }
  }

  sealed interface Effect : MviEffect {           // one-shot, never state
    data class PrintReceipt(val sale: Sale) : Effect
    data class Navigate(val screen: Screen) : Effect
    data class FocusField(val field: FieldId) : Effect
    data object OpenCashDrawer : Effect
  }
}
```

### The three types, and the rule that separates them

| Type | Meaning | Test |
|---|---|---|
| **State** | Everything needed to render the screen, right now. Immutable. | If the process were killed and this restored, the screen would look identical. |
| **Intent** | Something happened. User actions and internal results. | Past tense or a command — never "setX". |
| **Effect** | A one-shot side effect the UI must perform once. | Replaying it would be **wrong** (printing twice, navigating twice). |

> **The line that matters:** if it should survive a recomposition or a config change, it is **State**.
> If doing it twice would be a bug, it is an **Effect**. A snackbar/error banner is State (with a
> dismiss intent), not an Effect — otherwise it vanishes on rotation and can't be tested.

---

## 3. The Store

A thin base class, hand-rolled (~120 lines total). See [ADR-011](adr/ADR-011-mvi-unidirectional-presentation.md)
for why not a library.

```kotlin
// :core:ui/mvi/MviStore.kt   (commonMain)
abstract class MviStore<S : MviState, I : MviIntent, E : MviEffect>(
  initialState: S,
) : ViewModel() {                                    // androidx.lifecycle KMP ViewModel

  private val _state = MutableStateFlow(initialState)
  val state: StateFlow<S> = _state.asStateFlow()

  private val _effects = Channel<E>(Channel.BUFFERED) // Channel, not SharedFlow: consumed once
  val effects: Flow<E> = _effects.receiveAsFlow()

  private val intents = MutableSharedFlow<I>(extraBufferCapacity = 64)

  init {
    viewModelScope.launch {
      intents.collect { intent ->
        _state.update { current -> reduce(current, intent) }   // ordered, synchronous
        launch { handle(intent, _state.value) }                // concurrent: never stalls the pipeline
      }
    }
  }

  fun dispatch(intent: I) { intents.tryEmit(intent) }

  /** PURE. No I/O, no coroutines, no clock, no randomness. Fully unit-testable. */
  protected abstract fun reduce(state: S, intent: I): S

  /** Side effects: call use cases, emit Internal intents with results. */
  protected open suspend fun handle(intent: I, state: S) {}

  protected fun emit(effect: E) { _effects.trySend(effect) }
  protected fun dispatchInternal(intent: I) { intents.tryEmit(intent) }
}
```

**Two-phase handling is the whole design:**
1. `reduce` is pure and synchronous — every state transition is a function you can test with no mocks.
2. `handle` performs I/O by calling use cases and feeds results back as `Intent.Internal.*`, which go
   through `reduce` like anything else.

A use case result **never** writes state directly. It always re-enters as an intent. That single rule
is what keeps the state machine inspectable.

### Ordering guarantees — read this before implementing

| Guarantee | Detail |
|---|---|
| **Reductions are strictly ordered** | Intents reduce one at a time, in arrival order, on the store's scope. State transitions can never interleave. |
| **`handle` runs concurrently** | It is launched in a child coroutine, so a slow sale commit cannot stall a barcode scan arriving behind it. This matters: a serialized pipeline makes the POS feel frozen during I/O. |
| **Concurrent handlers must not race** | Serialize at the *state* level, not the coroutine level: `CompleteSaleClicked` sets `isLoading = true` in `reduce`, and `handle` ignores the intent when `state.isLoading` is already set. Double-submit becomes unrepresentable rather than merely unlikely. |
| **Effects are exactly-once** | `Channel`, not `SharedFlow` — no replay, no double print. |
| **Intent buffer is bounded** | `extraBufferCapacity = 64`. A scanner firing faster than that means something is wrong; `tryEmit` returning false should log, not silently drop. |

### Concrete store

```kotlin
class PosStore(
  private val completeSale: CompleteSale,
  private val resolvePrice: ResolvePrice,
  private val products: ProductRepository,
  private val principal: PrincipalProvider,
) : MviStore<State, Intent, Effect>(State()) {

  override fun reduce(state: State, intent: Intent) = when (intent) {
    is Intent.SearchChanged   -> state.copy(searchQuery = intent.query)
    is Intent.ChangeQty       -> state.withLineQty(intent.lineId, intent.qty).recalculated()
    is Intent.CompleteSaleClicked -> state.copy(isLoading = true, error = null)
    is Intent.ErrorDismissed  -> state.copy(error = null)
    is Intent.Internal.SearchLoaded  -> state.copy(searchResults = intent.results)
    is Intent.Internal.SaleCompleted -> State()                      // fresh cart
    is Intent.Internal.Failed        -> state.copy(isLoading = false, error = intent.error)
    else -> state
  }

  override suspend fun handle(intent: Intent, state: State) {
    when (intent) {
      is Intent.CompleteSaleClicked -> {
        if (state.isLoading) return                   // guard: double-submit is unrepresentable
        when (val r = completeSale(principal.require(), state.toDraft())) {
          is AppResult.Ok -> {
            dispatchInternal(Intent.Internal.SaleCompleted(r.value))
            emit(Effect.PrintReceipt(r.value))
            if (state.hasCashPayment) emit(Effect.OpenCashDrawer)
          }
          is AppResult.Err -> dispatchInternal(Intent.Internal.Failed(r.error.toKey()))
        }
      }
      else -> Unit
    }
  }
}
```

### Compose side — screens are stateless

```kotlin
@Composable
fun PosRoute(store: PosStore, navigator: Navigator, printer: ReceiptPrinter) {
  val state by store.state.collectAsStateWithLifecycle()

  LaunchedEffect(Unit) {
    store.effects.collect { effect ->
      when (effect) {
        is Effect.PrintReceipt -> printer.print(effect.sale.toReceipt())
        is Effect.Navigate     -> navigator.go(effect.screen)
        is Effect.FocusField   -> /* focus requester */
        Effect.OpenCashDrawer  -> printer.kickDrawer()
      }
    }
  }

  PosScreen(state = state, onIntent = store::dispatch)   // pure, previewable, no store
}
```

**Rules:**
- `PosScreen` takes `state` + `onIntent` and nothing else. It is previewable and has no injected deps.
- The `Route` composable is the only place that owns a store and consumes effects.
- Effects are collected **once**, in `LaunchedEffect(Unit)` at the route.
- Composables never construct `Intent.Internal.*`.

---

## 4. POS performance — the one place naive MVI hurts

A single `State` with a 40-line cart re-emits on every keystroke, and a naive `LazyColumn` recomposes
every row. On the barcode-scanning screen this is the difference between a usable till and an
abandoned one.

| Technique | Where |
|---|---|
| `key = { it.id }` in every `LazyColumn`/`items` | All lists |
| Immutable `data class` + `List` (Kotlin's read-only list is treated as stable by Compose's default rules; add `@Immutable`/`kotlinx.collections.immutable` if the compiler reports instability) | State types |
| Slice the flow: `state.map { it.totals }.distinctUntilChanged()` for expensive sub-trees | Totals panel |
| Debounce **search input**, never scan input — scans must feel instant | `handle`, 200ms on `SearchChanged` only |
| Keep derived totals in `reduce` (cheap arithmetic on Longs), never a DB round-trip per keystroke | `recalculated()` |
| Enable Compose compiler strong-skipping + run the compiler metrics report once in Phase 3 | Build config |

Measure it: **5-line sale under 40 seconds, keyboard-only** is a Phase 1 exit criterion, and scan-to-
line-added under 100ms is the Phase 3 target.

---

## 5. Testing

| Test | What it covers | Cost |
|---|---|---|
| **Reducer tests** — `reduce(state, intent) == expected` | Every state transition. No mocks, no coroutines, no dispatcher setup. | Cheap; write these |
| **Store tests** — `Turbine` over `state`, fake use cases | Intent → I/O → Internal intent → state sequence, and effects emitted | Medium; write for POS, returns, shift close |
| **Use case tests** in `:domain` | Business rules: pricing, discounts, return limits, shift math | **Highest ROI** |
| **Repository tests** in `:data` | Migrations, ledger↔level reconciliation, outbox completeness | Non-negotiable |
| Compose UI tests | Deferred past Phase 4 (see [architecture.md §8](architecture.md)) | — |

Because `reduce` is pure, most presentation bugs are caught by a test with **zero infrastructure**.
That is the practical reason MVI is worth the ceremony on a solo project.

---

## 6. Naming and file layout

```
feature/pos/
  PosContract.kt      State, Intent, Effect          (no logic)
  PosStore.kt         reduce + handle
  PosRoute.kt         store wiring + effect collection
  PosScreen.kt        stateless composable
  components/         CartTable.kt, TotalsPanel.kt, PaymentDialog.kt
  PosStoreTest.kt
  PosReducerTest.kt
```

| Convention | Rule |
|---|---|
| One contract per screen | Not per feature — `PosContract`, `ShiftCloseContract` |
| Intent names | User events past tense (`AddLineClicked`) or imperative (`AddLine`) — pick one and keep it. **ASSUMPTION:** imperative for user intents, past tense for `Internal` results |
| No `UiEvent`/`Action`/`Message` synonyms | One vocabulary: State, Intent, Effect |
| `error: ErrorKey?` | Never `String`. Arabic rendering happens in `:core:ui` |
| Stores are constructor-injected | Koin `factory`; the route scopes them |

---

## 7. Anti-patterns this forbids

1. **`var` in State**, or a `MutableList` inside it. State is deeply immutable.
2. **Calling a repository from a composable.** Composables receive `state` and emit intents.
3. **I/O inside `reduce`.** If it suspends, it belongs in `handle`.
4. **A use case result written straight to `_state`.** It re-enters as an `Internal` intent.
5. **Effects used for things that should be state** (errors, loading flags) — they vanish on
   recomposition and can't be asserted.
6. **A "god state"** shared across unrelated screens. One contract per screen.
7. **SQLDelight or DTO types in `State`.** Domain models only.
8. **Business rules in `reduce`.** Reduce arranges state; `:domain` decides what is legal. Pricing,
   return limits and credit checks live in use cases, not in the reducer.
9. **Navigation performed inside a store.** Stores emit `Effect.Navigate`; the route navigates.

---

## 8. Why this survives the later phases

| Later change | Presentation impact |
|---|---|
| Backend replaces local repositories (Phase 4) | **None.** Stores depend on domain interfaces |
| PIN auth → JWT (Phase 4) | **None.** `PrincipalProvider` is unchanged ([ADR-008](adr/ADR-008-auth-principal-abstraction.md)) |
| Android owner app (Phase 5) | Stores and contracts are commonMain — reused verbatim; only the Route/host differs |
| iOS / Web | Same. The KMP `ViewModel` artifact covers jvm/android/ios/wasm |
| A screen gets a new field | One line in `State`, one in `reduce`, one in the composable |
