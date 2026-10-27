# 3. `rememberValue`

A tiny utility that behaves like React's `useRef`: a mutable holder that is created once, can be read and written freely, and **never triggers recomposition**.

```kotlin
@Composable
fun <T> rememberValue(init: () -> T): RememberValue<T>
```

## Basic usage

Use it with `by`. No extra imports are needed, since `RememberValue` defines its own `getValue` / `setValue` operators.

```kotlin
@Composable
fun Example() {
    var taps by rememberValue { 0 }

    Button(onClick = { taps++ }) {
        Text("Tap me") // does NOT update: writing taps doesn't recompose
    }
}
```

`taps` changes on every click, but the UI doesn't change, because nothing observes it.

## Behavior summary

| Event | Value kept? |
|---|---|
| Recomposition | Yes (it is created with `remember`) |
| Write to the variable | Changes immediately, **no recomposition** |
| Composable leaves composition | No |
| Configuration change (rotation etc.) | No, it is re-initialized |
| Process death | No |

> **Note on "doesn't survive":** `rememberValue` is built on `remember { }`, so the holder object itself is stable across recompositions, exactly like `useRef`. What it does not do is outlive the composable or the Activity. If you need the value to survive configuration changes, use [`rememberValueInDataStore`](value-in-datastore.md) instead.

## `RememberValue<T>` class

```kotlin
class RememberValue<T>(private val init: () -> T) : EntryKey {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T)
}
```

- `init` is invoked once, when the holder is constructed.
- It implements `EntryKey` for consistency with the other delegates, but it **does not use keys**: there is no store involved.

## When to use it

- Handles to things that should not cause UI updates: `Job`, `Timer`, `Animator`, listeners.
- "Previous value" tracking.
- Counters or flags used by side effects (analytics, logging).
- Anything you would otherwise store with `remember { mutableStateOf(...) }` but do not want to observe.

## Examples

### Keep a debounce `Job`

```kotlin
@Composable
fun DebouncedField(onQuery: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var job by rememberValue<Job?> { null }
    var text by remember { mutableStateOf("") }

    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            text = new
            job?.cancel()
            job = scope.launch {
                delay(400)
                onQuery(new)
            }
        }
    )
}
```

### Track the previous value

```kotlin
@Composable
fun PriceTag(price: Int) {
    var previous by rememberValue { price }

    val trend = when {
        price > previous -> "▲"
        price < previous -> "▼"
        else -> "–"
    }

    SideEffect { previous = price }   // update after the frame is composed

    Text("$price $trend")
}
```

### One-shot flag

```kotlin
@Composable
fun Screen(onFirstShown: () -> Unit) {
    var reported by rememberValue { false }

    LaunchedEffect(Unit) {
        if (!reported) {
            reported = true
            onFirstShown()
        }
    }
}
```

## Gotchas

1. **No UI updates.** If you read the value in composition and expect the UI to follow writes, use `mutableStateOf` or [`rememberStateInDataStore`](state-in-datastore.md).
2. **Avoid writing during composition.** Writes are side effects. Do them in `SideEffect`, `LaunchedEffect`, or event callbacks.
3. **No keys parameter.** It is `remember { }` under the hood without keys, so changing inputs does not reset it. If you need a reset-on-input-change, use plain `remember(input) { ... }`.
4. **Not thread-safe.** Plain field, no synchronization.
