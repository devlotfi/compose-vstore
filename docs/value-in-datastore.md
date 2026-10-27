# 5. Values in DataStore: `rememberValueInDataStore`

A **non-observable** value stored in the `DataStore`. It survives recomposition and configuration changes (as long as the store lives), but reading and writing it never triggers recomposition.

Think of it as `rememberValue` (the `useRef` analogue) with ViewModel lifetime.

```kotlin
@Composable
fun <T> DataStore.rememberValueInDataStore(
    key: String,
    init: () -> T,
): RememberValueInDataStore<T>
```

## Basic usage

```kotlin
@Composable
fun FeedScreen(vm: MainViewModel = viewModel()) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = 0
    )
    var savedIndex by vm.store.rememberValueInDataStore("FeedScreen") { 0 }

    // Restore on first composition
    LaunchedEffect(Unit) { listState.scrollToItem(savedIndex) }

    // Save continuously, without triggering recomposition
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { savedIndex = it }
    }

    LazyColumn(state = listState) { /* ... */ }
}
```

No extra imports are needed. `RememberValueInDataStore` defines its own `getValue`/`setValue` operators.

## How it works

- Key: `"$key/${property.name}"`, same rule as state.
- `getValue` calls `dataStore.getValue(finalKey, init)`, which runs `values.getOrPut(finalKey) { init() }`.
- `setValue` calls `dataStore.setValue(finalKey, value)`, which writes to the map.
- **Every read goes to the map** (it is a delegate; there is no local cache). Reads are cheap, but if you use the value repeatedly in a hot path, copy it into a local `val`.

## State vs value: which one?

| Question | `rememberStateInDataStore` | `rememberValueInDataStore` |
|---|---|---|
| Does the UI display it? | Yes | No |
| Should writes redraw the UI? | Yes | No |
| High-frequency writes (scroll, drag, ticks)? | Avoid | Good fit |
| Holds an object like a `Job` or cache? | Rarely | Yes |

Using a value avoids wasteful recompositions for data that only needs to be *remembered*, not *rendered*.

## Direct API

```kotlin
val last = store.getValue("FeedScreen/savedIndex") { 0 }
store.setValue("FeedScreen/savedIndex", 12)
```

Use the same key format (`"<key>/<propertyName>"`) so the composable's delegate reads the same entry.

## Examples

### Remember that a one-time animation already played

```kotlin
@Composable
fun Onboarding(vm: MainViewModel = viewModel()) {
    var introPlayed by vm.store.rememberValueInDataStore("Onboarding") { false }

    LaunchedEffect(Unit) {
        if (!introPlayed) {
            playIntroAnimation()
            introPlayed = true     // rotate → animation does not replay
        }
    }
}
```

### Cache an expensive computation across rotations

```kotlin
@Composable
fun Report(data: List<Row>, vm: MainViewModel = viewModel()) {
    val summary by vm.store.rememberValueInDataStore("Report") { computeSummary(data) }
    Text(summary.title)
}
```

(`computeSummary` runs once per store, not per rotation. If `data` changes, invalidate manually. See the warning below.)

### Hold a long-lived job

```kotlin
var pollingJob by vm.store.rememberValueInDataStore<Job?>("Dashboard") { null }
```

## Gotchas

- **Not observable.** If a composable displays the value and you change it, the screen will not update. Use state for that.
- **Initializer ignored after first read.** If your cached value depends on changing inputs, you need to overwrite it yourself.
- **Same type per key**, as with state (unchecked cast).
- **Leak awareness:** do not store `Context`, `Activity`, or view references.

Next: [API reference](api-reference.md)
