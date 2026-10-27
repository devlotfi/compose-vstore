# vstore

> ViewModel-backed state and value storage for Jetpack Compose, with automatic per-property keys.

`vstore` lets you keep Compose state in a `DataStore` object that lives inside your `ViewModel`. Because the store lives as long as the ViewModel, the data survives **recomposition**, **configuration changes** (rotation, dark mode, locale, etc.) and **navigation back-stack round trips** (as long as the ViewModel is retained). Each entry key is built from a name you give (usually the composable name) plus the **property name, which is added automatically**.

```kotlin
@Composable
fun CounterScreen(vm: MainViewModel = viewModel()) {
    var count by vm.store.rememberStateInDataStore("CounterScreen") { 0 }

    Button(onClick = { count++ }) { Text("Clicked $count times") }
}
```

Rotate the device: `count` is still there.

**Package:** `com.devlotfi.testlol.vstore`

---

## What's in the box

| API | Survives recomposition | Survives config change | Triggers recomposition | Backed by |
|---|---|---|---|---|
| `rememberValue { }` | Yes | No | No | Compose `remember` |
| `DataStore.rememberStateInDataStore(key) { }` | Yes | Yes (while the store lives) | **Yes** | `MutableState` in the store |
| `DataStore.rememberValueInDataStore(key) { }` | Yes | Yes (while the store lives) | No | Plain map in the store |

## Documentation index

| # | Document | What it covers |
|---|---|---|
| 1 | [Getting started](getting-started.md) | Setup, imports, wiring a `DataStore` into a ViewModel, first example |
| 2 | [Concepts and the key system](concepts-and-key-system.md) | Mental model, how keys are built, lifetimes, how the pieces connect |
| 3 | [`rememberValue`](remember-value.md) | The local, non-observable `useRef`-style holder |
| 4 | [State in DataStore](state-in-datastore.md) | `rememberStateInDataStore`, observable state that survives config changes |
| 5 | [Values in DataStore](value-in-datastore.md) | `rememberValueInDataStore`, non-observable persisted values |
| 6 | [API reference](api-reference.md) | Every public class, function and signature |
| 7 | [Best practices and pitfalls](best-practices-and-pitfalls.md) | Key collisions, cleanup, threading, type safety, limits |
| 8 | [Recipes](recipes.md) | Forms, lists, tabs, scroll position, logout cleanup, testing |

## Choosing the right tool

```
Do you need this data to outlive the composable's time in composition
(config change, navigation, etc.)?
├── No  → rememberValue { }                    (local, cheap, no store needed)
└── Yes → Should the UI update when it changes?
          ├── Yes → rememberStateInDataStore   (observable)
          └── No  → rememberValueInDataStore   (plain value, no recomposition)
```
